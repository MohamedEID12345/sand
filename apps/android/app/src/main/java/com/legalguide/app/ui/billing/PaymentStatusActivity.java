package com.legalguide.app.ui.billing;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;

import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;

import com.legalguide.app.R;
import com.legalguide.app.core.net.ApiClient;
import com.legalguide.app.core.net.Calls;
import com.legalguide.app.core.session.Session;
import com.legalguide.app.core.util.Dates;
import com.legalguide.app.core.util.Intents;
import com.legalguide.app.core.util.Json;
import com.legalguide.app.core.util.Money;
import com.legalguide.app.core.util.Texts;
import com.legalguide.app.data.model.Order;
import com.legalguide.app.data.model.PaymentInfo;
import com.legalguide.app.databinding.ActivityPaymentStatusBinding;
import com.legalguide.app.ui.common.BaseActivity;
import com.legalguide.app.ui.common.Tone;
import com.legalguide.app.ui.common.Ui;
import com.legalguide.app.ui.main.MainActivity;

/**
 * Payment progress. Opens the Fawry hosted checkout (REDIRECT) or shows a FawryPay reference number
 * (REFERENCE), then asks the backend for the order state. Only the backend — via Fawry webhook or the
 * status API — decides that an order is PAID. Also receives legalguide://payment/return?orderId=...
 * (singleTask: the return deep link closes the Custom Tab above it and lands in onNewIntent).
 */
public class PaymentStatusActivity extends BaseActivity {
    private static final String E_ORDER = "order_id", E_PAYMENT = "payment_json";
    private static final long POLL_MS = 5000, POLL_MAX_REDIRECT_MS = 10 * 60 * 1000, POLL_MAX_REFERENCE_MS = 2 * 60 * 1000;

    private ActivityPaymentStatusBinding b;
    private String orderId;
    @Nullable private PaymentInfo payment;
    @Nullable private Order order;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private long pollStarted;
    private boolean checkoutOpened;
    private boolean finalState;

    public static void start(Context c, String orderId, @Nullable PaymentInfo payment) {
        Intent i = new Intent(c, PaymentStatusActivity.class).putExtra(E_ORDER, orderId);
        if (payment != null) i.putExtra(E_PAYMENT, Json.toJson(payment));
        c.startActivity(i);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!ensureSession()) return;
        b = ActivityPaymentStatusBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        applyInsets(b.getRoot());
        setupToolbar(b.appBar.toolbar, R.string.payment_title, true);
        if (savedInstanceState != null) checkoutOpened = savedInstanceState.getBoolean("opened");
        readIntent(getIntent());
        if (orderId == null) {
            finish();
            return;
        }
        b.copyReference.setOnClickListener(v -> {
            if (payment != null && payment.referenceNumber != null) Intents.copy(this, payment.referenceNumber);
        });
        b.checkNow.setOnClickListener(v -> verify());
        b.openCheckout.setOnClickListener(v -> openCheckout());
        b.done.setOnClickListener(v -> goHome());
        renderPending();
        pollStarted = System.currentTimeMillis();
        if (isRedirect() && !checkoutOpened) openCheckout();
        else poll();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        String previous = orderId;
        readIntent(intent);
        if (orderId != null && !orderId.equals(previous)) {
            // A different order was started while this screen existed: reset its state.
            handler.removeCallbacksAndMessages(null);
            finalState = false;
            order = null;
            checkoutOpened = intent.getData() != null;
            pollStarted = System.currentTimeMillis();
            renderPending();
            if (isRedirect() && !checkoutOpened) {
                openCheckout();
                return;
            }
        }
        verify(); // returned from the hosted checkout, or re-opened
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (checkoutOpened && !finalState && orderId != null) verify();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean("opened", checkoutOpened);
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    private void readIntent(Intent i) {
        Uri data = i.getData();
        if (data != null && "payment".equals(data.getHost())) {
            String id = data.getQueryParameter("orderId");
            if (id != null) orderId = id;
            checkoutOpened = true;
        }
        String extraOrder = i.getStringExtra(E_ORDER);
        if (extraOrder != null) {
            if (!extraOrder.equals(orderId)) payment = null;
            orderId = extraOrder;
        }
        PaymentInfo p = Json.fromJson(i.getStringExtra(E_PAYMENT), PaymentInfo.class);
        if (p != null) payment = p;
    }

    private boolean isRedirect() {
        return payment != null && "REDIRECT".equals(payment.type) && !Texts.blank(payment.checkoutUrl);
    }

    private void openCheckout() {
        if (payment == null || Texts.blank(payment.checkoutUrl)) return;
        checkoutOpened = true;
        Intents.openUrl(this, payment.checkoutUrl);
    }

    private void poll() {
        handler.removeCallbacksAndMessages(null);
        if (finalState || isFinishing()) return;
        Calls.enqueue(this, ApiClient.api().order(orderId), this::onOrder, e -> scheduleNext());
    }

    private void verify() {
        b.checkNow.setEnabled(false);
        Calls.enqueue(this, ApiClient.api().verifyOrder(orderId), o -> {
            b.checkNow.setEnabled(true);
            onOrder(o);
        }, e -> {
            b.checkNow.setEnabled(true);
            scheduleNext();
        });
    }

    private void scheduleNext() {
        long max = isRedirect() ? POLL_MAX_REDIRECT_MS : POLL_MAX_REFERENCE_MS;
        if (!finalState && System.currentTimeMillis() - pollStarted < max) handler.postDelayed(this::poll, POLL_MS);
    }

    private void onOrder(@Nullable Order o) {
        if (o == null) {
            scheduleNext();
            return;
        }
        order = o;
        if (payment == null && o.referenceNumber != null) {
            payment = new PaymentInfo();
            payment.type = "REFERENCE";
            payment.referenceNumber = o.referenceNumber;
            payment.expiresAt = o.expiresAt;
        }
        String s = o.status == null ? "PENDING" : o.status;
        switch (s) {
            case "PAID":
                finalState = true;
                renderPaid(o);
                break;
            case "FAILED":
            case "CANCELLED":
            case "EXPIRED":
            case "REFUNDED":
                finalState = true;
                renderFailed(o);
                break;
            case "PENDING_VERIFICATION":
                finalState = true;
                renderState(R.drawable.ic_clock, Tone.WARNING, getString(R.string.payment_review_title), getString(R.string.payment_review_message));
                b.done.setVisibility(View.VISIBLE);
                break;
            default:
                renderPending();
                scheduleNext();
        }
        renderSummary(o);
    }

    private void renderPending() {
        boolean reference = payment != null && payment.referenceNumber != null;
        if (reference) {
            renderState(R.drawable.ic_receipt, Tone.GOLD, getString(R.string.payment_reference_title), getString(R.string.payment_reference_message));
            b.referenceBox.setVisibility(View.VISIBLE);
            b.reference.setText(payment.referenceNumber);
            Ui.text(b.referenceExpiry, payment.expiresAt != null ? getString(R.string.payment_expires, Dates.dayTime(payment.expiresAt)) : null);
        } else {
            renderState(R.drawable.ic_timer, Tone.INFO, getString(R.string.payment_waiting_title), getString(R.string.payment_waiting_message));
            b.referenceBox.setVisibility(View.GONE);
        }
        Ui.text(b.instructions, payment != null ? payment.instructions : null);
        b.progress.setVisibility(View.VISIBLE);
        b.checkNow.setVisibility(View.VISIBLE);
        b.openCheckout.setVisibility(isRedirect() ? View.VISIBLE : View.GONE);
        b.done.setVisibility(reference ? View.VISIBLE : View.GONE);
        b.done.setText(R.string.payment_pay_later);
    }

    private void renderPaid(Order o) {
        renderState(R.drawable.ic_check_circle, Tone.SUCCESS, getString(R.string.payment_success_title),
                o.creditsGranted > 0 ? getString(R.string.payment_success_credits, o.creditsGranted) : getString(R.string.payment_success_message));
        b.referenceBox.setVisibility(View.GONE);
        b.instructions.setVisibility(View.GONE);
        b.done.setVisibility(View.VISIBLE);
        b.done.setText(R.string.payment_done);
        Calls.enqueue(this, ApiClient.api().bootstrap(), boot -> Session.get().setBootstrap(boot), null);
    }

    private void renderFailed(Order o) {
        renderState(R.drawable.ic_error, Tone.DANGER, getString(R.string.payment_failed_title),
                Texts.blank(o.failureReason) ? getString(R.string.payment_failed_message) : o.failureReason);
        b.referenceBox.setVisibility(View.GONE);
        b.done.setVisibility(View.VISIBLE);
        b.done.setText(R.string.payment_try_again);
        b.done.setOnClickListener(v -> {
            startActivity(new Intent(this, PlansActivity.class));
            finish();
        });
    }

    private void renderState(@DrawableRes int icon, Tone tone, String title, String message) {
        b.stateIcon.setImageResource(icon);
        tone.tile(b.stateTile, b.stateIcon);
        b.stateTitle.setText(title);
        b.stateMessage.setText(message);
        if (finalState) {
            b.progress.setVisibility(View.GONE);
            b.checkNow.setVisibility(View.GONE);
            b.openCheckout.setVisibility(View.GONE);
        }
    }

    private void renderSummary(Order o) {
        b.summary.removeAllViews();
        Ui.kv(b.summary, getString(R.string.payment_item), o.itemName);
        Ui.kv(b.summary, getString(R.string.payment_amount), Money.format(o.total > 0 ? o.total : o.amount, o.currency));
        Ui.kv(b.summary, getString(R.string.payment_method), o.paymentMethod);
        Ui.kv(b.summary, getString(R.string.payment_order_id), o.id);
        b.summaryCard.setVisibility(View.VISIBLE);
    }

    private void goHome() {
        startActivity(new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
        finish();
    }
}
