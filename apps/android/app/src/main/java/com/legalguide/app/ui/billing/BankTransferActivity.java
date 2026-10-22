package com.legalguide.app.ui.billing;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;

import com.legalguide.app.R;
import com.legalguide.app.core.net.ApiClient;
import com.legalguide.app.core.net.UriRequestBody;
import com.legalguide.app.core.util.Intents;
import com.legalguide.app.core.util.Json;
import com.legalguide.app.core.util.Money;
import com.legalguide.app.core.util.Texts;
import com.legalguide.app.data.model.BankDetails;
import com.legalguide.app.data.model.Order;
import com.legalguide.app.data.model.PaymentInfo;
import com.legalguide.app.databinding.ActivityBankTransferBinding;
import com.legalguide.app.ui.common.BaseActivity;
import com.legalguide.app.ui.common.Ui;

/** Manual bank transfer: shows the admin-configured account and uploads the transfer receipt for review. */
public class BankTransferActivity extends BaseActivity {
    private static final String E_ORDER = "order_json", E_PAYMENT = "payment_json";

    private ActivityBankTransferBinding b;
    private Order order;
    @Nullable private UriRequestBody.FileInfo proof;

    private final ActivityResultLauncher<String[]> picker = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(), this::onPicked);

    public static void start(Context c, Order order, @Nullable PaymentInfo payment) {
        c.startActivity(new Intent(c, BankTransferActivity.class).putExtra(E_ORDER, Json.toJson(order))
                .putExtra(E_PAYMENT, payment == null ? null : Json.toJson(payment)));
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!ensureSession()) return;
        b = ActivityBankTransferBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        applyInsets(b.getRoot());
        setupToolbar(b.appBar.toolbar, R.string.bank_title, true);
        order = Json.fromJson(getIntent().getStringExtra(E_ORDER), Order.class);
        PaymentInfo p = Json.fromJson(getIntent().getStringExtra(E_PAYMENT), PaymentInfo.class);
        if (order == null) {
            finish();
            return;
        }
        b.amount.setText(Money.format(order.total > 0 ? order.total : order.amount, order.currency));
        BankDetails d = p != null ? p.bankDetails : null;
        b.details.removeAllViews();
        if (d != null) {
            Ui.kv(b.details, getString(R.string.bank_name), d.bankName);
            Ui.kv(b.details, getString(R.string.bank_account_name), d.accountName);
            copyable(Ui.kv(b.details, getString(R.string.bank_account_number), d.accountNumber), d.accountNumber);
            copyable(Ui.kv(b.details, getString(R.string.bank_iban), d.iban), d.iban);
            if (!Texts.blank(d.swift)) Ui.kv(b.details, getString(R.string.bank_swift), d.swift);
            if (!Texts.blank(d.branch)) Ui.kv(b.details, getString(R.string.bank_branch), d.branch);
        }
        Ui.text(b.instructions, d != null && !Texts.blank(d.instructions) ? d.instructions : p != null ? p.instructions : null);
        b.reference.setText(getString(R.string.bank_reference, order.id));
        b.pick.setOnClickListener(v -> picker.launch(new String[]{"image/*", "application/pdf"}));
        b.submit.setOnClickListener(v -> submit());
        b.later.setOnClickListener(v -> finish());
    }

    private void copyable(android.widget.TextView tv, @Nullable String value) {
        if (Texts.blank(value)) return;
        tv.setOnClickListener(v -> Intents.copy(this, value));
        tv.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, R.drawable.ic_copy, 0);
        tv.setCompoundDrawablePadding(Ui.dp(this, 6));
    }

    private void onPicked(@Nullable Uri uri) {
        if (uri == null) return;
        proof = UriRequestBody.inspect(this, uri);
        b.proofName.setText(Texts.joinNonBlank(" • ", proof.name, Texts.fileSize(proof.size)));
        b.proofName.setVisibility(View.VISIBLE);
        b.submit.setEnabled(true);
    }

    private void submit() {
        if (proof == null) {
            snack(getString(R.string.bank_pick_first));
            return;
        }
        Ui.loading(b.submit, true);
        call(ApiClient.api().uploadBankProof(order.id, UriRequestBody.part(this, "file", proof),
                UriRequestBody.text(Texts.trim(b.note.getText()))), o -> {
            Ui.loading(b.submit, false);
            PaymentStatusActivity.start(this, order.id, null);
            finish();
        }, e -> {
            Ui.loading(b.submit, false);
            showError(e);
        });
    }
}
