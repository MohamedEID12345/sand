package com.legalguide.app.ui.billing;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;

import androidx.annotation.Nullable;

import com.legalguide.app.BuildConfig;
import com.legalguide.app.R;
import com.legalguide.app.core.net.ApiClient;
import com.legalguide.app.core.util.Json;
import com.legalguide.app.core.util.Money;
import com.legalguide.app.core.util.Texts;
import com.legalguide.app.data.model.CheckoutResponse;
import com.legalguide.app.data.model.PaymentMethod;
import com.legalguide.app.data.model.PromoResult;
import com.legalguide.app.databinding.ActivityCheckoutBinding;
import com.legalguide.app.databinding.ItemPaymentMethodBinding;
import com.legalguide.app.ui.common.BaseActivity;
import com.legalguide.app.ui.common.Ui;

import java.util.ArrayList;
import java.util.List;

/**
 * Order review: promo code, payment method, total. The backend creates the order with the provider
 * (Fawry) and returns how to pay; the app never marks anything as paid.
 */
public class CheckoutActivity extends BaseActivity {
    private static final String E_TYPE = "item_type", E_ID = "item_id", E_NAME = "item_name", E_SUB = "item_sub",
            E_PRICE = "price", E_CURRENCY = "currency";

    private ActivityCheckoutBinding b;
    private String itemType, itemId, currency;
    private double price;
    @Nullable private PromoResult promo;
    @Nullable private PaymentMethod selected;
    private final List<ItemPaymentMethodBinding> methodViews = new ArrayList<>();

    public static void start(Context c, String itemType, String itemId, String name, @Nullable String subtitle,
                             double price, @Nullable String currency) {
        c.startActivity(new Intent(c, CheckoutActivity.class).putExtra(E_TYPE, itemType).putExtra(E_ID, itemId)
                .putExtra(E_NAME, name).putExtra(E_SUB, subtitle).putExtra(E_PRICE, price).putExtra(E_CURRENCY, currency));
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!ensureSession()) return;
        b = ActivityCheckoutBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        applyInsets(b.getRoot());
        setupToolbar(b.appBar.toolbar, R.string.checkout_title, true);

        Intent i = getIntent();
        itemType = i.getStringExtra(E_TYPE);
        itemId = i.getStringExtra(E_ID);
        price = i.getDoubleExtra(E_PRICE, 0);
        currency = i.getStringExtra(E_CURRENCY);
        b.itemName.setText(i.getStringExtra(E_NAME));
        Ui.text(b.itemSubtitle, i.getStringExtra(E_SUB));
        b.itemPrice.setText(Money.format(price, currency));
        b.itemIcon.setImageResource("PLAN".equals(itemType) ? R.drawable.ic_crown : R.drawable.ic_bolt);

        b.applyPromo.setOnClickListener(v -> applyPromo());
        b.pay.setOnClickListener(v -> pay());
        updateTotals();
        loadMethods();
    }

    private void loadMethods() {
        b.methodsProgress.setVisibility(View.VISIBLE);
        call(ApiClient.api().paymentMethods(itemType), page -> {
            b.methodsProgress.setVisibility(View.GONE);
            b.methods.removeAllViews();
            methodViews.clear();
            List<PaymentMethod> list = page != null && page.items != null ? page.items : new ArrayList<>();
            if (list.isEmpty()) {
                b.methodsEmpty.setVisibility(View.VISIBLE);
                b.pay.setEnabled(false);
                return;
            }
            for (PaymentMethod m : list) {
                ItemPaymentMethodBinding mb = ItemPaymentMethodBinding.inflate(LayoutInflater.from(this), b.methods, true);
                mb.methodLabel.setText(m.label);
                Ui.text(mb.methodDescription, m.description);
                mb.methodIcon.setImageResource(icon(m));
                mb.getRoot().setOnClickListener(v -> select(m, mb));
                mb.methodRadio.setOnClickListener(v -> select(m, mb));
                methodViews.add(mb);
            }
            select(list.get(0), methodViews.get(0));
        }, e -> {
            b.methodsProgress.setVisibility(View.GONE);
            showError(e);
        });
    }

    private static int icon(PaymentMethod m) {
        if ("MANUAL".equals(m.type)) return R.drawable.ic_bank;
        if ("REFERENCE".equals(m.type)) return R.drawable.ic_receipt;
        if (m.code != null && m.code.contains("WALLET")) return R.drawable.ic_wallet;
        return R.drawable.ic_card;
    }

    private void select(PaymentMethod m, ItemPaymentMethodBinding view) {
        selected = m;
        for (ItemPaymentMethodBinding mb : methodViews) {
            boolean on = mb == view;
            mb.methodRadio.setChecked(on);
            mb.getRoot().setChecked(on);
            mb.getRoot().setStrokeColor(getColor(on ? R.color.lg_primary : R.color.lg_outline_variant));
        }
    }

    private void applyPromo() {
        String code = Texts.trim(b.promo.getText()).toUpperCase();
        b.promoLayout.setError(null);
        b.promoLayout.setHelperText(null);
        if (code.isEmpty()) {
            promo = null;
            updateTotals();
            return;
        }
        Ui.loading(b.applyPromo, true);
        call(ApiClient.api().validatePromo(Json.map("code", code, "itemType", itemType, "itemId", itemId)), r -> {
            Ui.loading(b.applyPromo, false);
            if (r != null && r.valid) {
                promo = r;
                b.promoLayout.setHelperText(Texts.blank(r.message) ? getString(R.string.promo_applied) : r.message);
            } else {
                promo = null;
                b.promoLayout.setError(r != null && !Texts.blank(r.message) ? r.message : getString(R.string.promo_invalid));
            }
            updateTotals();
        }, e -> {
            Ui.loading(b.applyPromo, false);
            promo = null;
            b.promoLayout.setError(e.message != null ? e.message : getString(R.string.promo_invalid));
            updateTotals();
        });
    }

    private void updateTotals() {
        double discount = promo != null ? promo.discountAmount : 0;
        double total = promo != null && promo.finalAmount > 0 ? promo.finalAmount : Math.max(0, price - discount);
        b.totals.removeAllViews();
        Ui.kv(b.totals, getString(R.string.checkout_subtotal), Money.format(price, currency));
        if (discount > 0) Ui.kv(b.totals, getString(R.string.checkout_discount), "- " + Money.format(discount, currency));
        if (promo != null && promo.bonusCredits > 0) Ui.kv(b.totals, getString(R.string.checkout_bonus), getString(R.string.credits_count, String.valueOf(promo.bonusCredits)));
        b.total.setText(Money.format(total, currency));
        b.pay.setText(getString(R.string.checkout_pay, Money.format(total, currency)));
    }

    private void pay() {
        if (selected == null) return;
        Ui.loading(b.pay, true);
        call(ApiClient.api().createOrder(Json.map("itemType", itemType, "itemId", itemId, "paymentMethod", selected.code,
                "promoCode", promo != null ? promo.code : null,
                "returnUrl", BuildConfig.PAYMENT_RETURN_SCHEME + "://payment/return")), this::onOrder, e -> {
            Ui.loading(b.pay, false);
            showError(e);
        });
    }

    private void onOrder(@Nullable CheckoutResponse r) {
        Ui.loading(b.pay, false);
        if (r == null || r.order == null) return;
        String type = r.payment != null ? r.payment.type : null;
        if ("MANUAL".equals(type)) {
            BankTransferActivity.start(this, r.order, r.payment);
        } else {
            PaymentStatusActivity.start(this, r.order.id, r.payment);
        }
        finish();
    }
}
