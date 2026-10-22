package com.legalguide.app.ui.clients;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.Nullable;

import com.legalguide.app.R;
import com.legalguide.app.core.net.ApiClient;
import com.legalguide.app.core.util.FieldValidator;
import com.legalguide.app.core.util.Json;
import com.legalguide.app.core.util.Texts;
import com.legalguide.app.data.model.Client;
import com.legalguide.app.databinding.ActivityClientFormBinding;
import com.legalguide.app.ui.common.BaseActivity;
import com.legalguide.app.ui.common.Ui;

import java.util.Map;

public class ClientFormActivity extends BaseActivity {
    public static final String RESULT_ID = "result_client_id", RESULT_NAME = "result_client_name";
    private static final String EXTRA_ID = "client_id";

    private ActivityClientFormBinding b;
    @Nullable private String clientId;

    public static void edit(Context c, String id) {
        c.startActivity(new Intent(c, ClientFormActivity.class).putExtra(EXTRA_ID, id));
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!ensureSession()) return;
        b = ActivityClientFormBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        applyInsets(b.getRoot());
        clientId = getIntent().getStringExtra(EXTRA_ID);
        setupToolbar(b.appBar.toolbar, clientId == null ? R.string.client_new : R.string.client_edit, true);
        b.save.setOnClickListener(v -> save());
        if (clientId != null) {
            b.save.setEnabled(false);
            call(ApiClient.api().client(clientId), c -> {
                b.save.setEnabled(true);
                if (c == null) return;
                b.name.setText(c.name);
                b.phone.setText(c.phone);
                b.email.setText(c.email);
                b.address.setText(c.address);
                b.notes.setText(c.notes);
                if (c.feesTotal != null) b.feesTotal.setText(plain(c.feesTotal));
                if (c.feesPaid != null) b.feesPaid.setText(plain(c.feesPaid));
            });
        }
    }

    private static String plain(double d) {
        return d == Math.rint(d) ? String.valueOf((long) d) : String.valueOf(d);
    }

    @Nullable
    private static Double number(CharSequence s) {
        String v = Texts.asciiDigits(Texts.trim(s)).replace(",", "");
        if (v.isEmpty()) return null;
        try {
            return Double.parseDouble(v);
        } catch (NumberFormatException e) {
            return -1d;
        }
    }

    private void save() {
        b.nameLayout.setError(null);
        b.phoneLayout.setError(null);
        b.emailLayout.setError(null);
        b.feesTotalLayout.setError(null);
        b.feesPaidLayout.setError(null);
        String name = Texts.trim(b.name.getText());
        String phone = Texts.asciiDigits(Texts.trim(b.phone.getText()));
        String email = Texts.trim(b.email.getText());
        Double total = number(b.feesTotal.getText());
        Double paid = number(b.feesPaid.getText());
        boolean ok = true;
        if (name.length() < 2) { b.nameLayout.setError(getString(R.string.error_name)); ok = false; }
        if (!phone.isEmpty() && !FieldValidator.isEgyptianMobile(phone)) { b.phoneLayout.setError(getString(R.string.error_phone)); ok = false; }
        if (!email.isEmpty() && !FieldValidator.isEmail(email)) { b.emailLayout.setError(getString(R.string.error_email)); ok = false; }
        if (total != null && total < 0) { b.feesTotalLayout.setError(getString(R.string.error_number)); ok = false; }
        if (paid != null && paid < 0) { b.feesPaidLayout.setError(getString(R.string.error_number)); ok = false; }
        if (!ok) return;
        Map<String, Object> body = Json.map("name", name, "phone", Texts.nullIfBlank(phone), "email", Texts.nullIfBlank(email),
                "address", Texts.nullIfBlank(b.address.getText()), "notes", Texts.nullIfBlank(b.notes.getText()),
                "feesTotal", total, "feesPaid", paid);
        Ui.loading(b.save, true);
        retrofit2.Call<Client> call = clientId == null ? ApiClient.api().createClient(body) : ApiClient.api().updateClient(clientId, body);
        call(call, c -> {
            Ui.loading(b.save, false);
            if (c != null) setResult(RESULT_OK, new Intent().putExtra(RESULT_ID, c.id).putExtra(RESULT_NAME, c.name));
            toast(R.string.saved);
            finish();
        }, e -> {
            Ui.loading(b.save, false);
            if (e.fieldErrors.containsKey("phone")) b.phoneLayout.setError(e.fieldErrors.get("phone"));
            else if (e.fieldErrors.containsKey("name")) b.nameLayout.setError(e.fieldErrors.get("name"));
            else showError(e);
        });
    }
}
