package com.legalguide.app.ui.auth;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;

import androidx.annotation.Nullable;

import com.google.android.material.textfield.TextInputLayout;
import com.legalguide.app.R;
import com.legalguide.app.core.net.ApiClient;
import com.legalguide.app.core.session.Session;
import com.legalguide.app.core.util.FieldValidator;
import com.legalguide.app.core.util.Intents;
import com.legalguide.app.core.util.Json;
import com.legalguide.app.core.util.Texts;
import com.legalguide.app.data.model.AppConfig;
import com.legalguide.app.data.model.Option;
import com.legalguide.app.databinding.ActivityRegisterBinding;
import com.legalguide.app.ui.common.BaseActivity;
import com.legalguide.app.ui.common.Ui;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class RegisterActivity extends BaseActivity {
    private ActivityRegisterBinding b;
    private final List<Option> governorates = new ArrayList<>();
    private final List<Option> degrees = new ArrayList<>();

    @Override
    protected boolean requiresAuth() {
        return false;
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityRegisterBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        applyInsets(b.getRoot());
        setupToolbar(b.appBar.toolbar, R.string.register_title, true);

        AppConfig cfg = Session.get().config();
        List<String> govs = cfg != null && cfg.governorates != null && !cfg.governorates.isEmpty()
                ? cfg.governorates : Arrays.asList(getResources().getStringArray(R.array.governorates));
        for (String g : govs) governorates.add(new Option(g, g));
        Ui.dropdown(b.governorate, governorates);
        if (cfg != null && cfg.degrees != null && !cfg.degrees.isEmpty()) {
            degrees.addAll(cfg.degrees);
        } else {
            String[] labels = getResources().getStringArray(R.array.degrees);
            String[] values = getResources().getStringArray(R.array.degree_values);
            for (int i = 0; i < labels.length; i++) degrees.add(new Option(values[i], labels[i]));
        }
        Ui.dropdown(b.degree, degrees);

        if (cfg != null && (cfg.freeServiceUses > 0 || cfg.freeCredits > 0)) {
            b.freeTrial.setText(getString(R.string.register_free_trial, cfg.freeServiceUses, cfg.freeCredits));
        }
        b.termsLink.setOnClickListener(v -> {
            if (cfg != null) Intents.openUrl(this, cfg.termsUrl);
        });
        b.submit.setOnClickListener(v -> submit());
        b.login.setOnClickListener(v -> {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
        });
    }

    private void submit() {
        TextInputLayout[] all = {b.nameLayout, b.emailLayout, b.phoneLayout, b.governorateLayout, b.syndicateLayout,
                b.degreeLayout, b.passwordLayout, b.confirmLayout};
        for (TextInputLayout l : all) l.setError(null);
        String name = Texts.trim(b.name.getText());
        String email = Texts.trim(b.email.getText());
        String phone = Texts.asciiDigits(Texts.trim(b.phone.getText()));
        String gov = Ui.optionValue(governorates, b.governorate.getText());
        String syndicate = Texts.asciiDigits(Texts.trim(b.syndicate.getText()));
        String degree = Ui.optionValue(degrees, b.degree.getText());
        String pw = Texts.trim(b.password.getText());
        String confirm = Texts.trim(b.confirm.getText());

        boolean ok = true;
        if (name.length() < 3) { b.nameLayout.setError(getString(R.string.error_name)); ok = false; }
        if (!FieldValidator.isEmail(email)) { b.emailLayout.setError(getString(R.string.error_email)); ok = false; }
        if (!FieldValidator.isEgyptianMobile(phone)) { b.phoneLayout.setError(getString(R.string.error_phone)); ok = false; }
        if (gov == null) { b.governorateLayout.setError(getString(R.string.error_required)); ok = false; }
        if (syndicate.isEmpty()) { b.syndicateLayout.setError(getString(R.string.error_required)); ok = false; }
        if (degree == null) { b.degreeLayout.setError(getString(R.string.error_required)); ok = false; }
        if (!FieldValidator.isStrongPassword(pw)) { b.passwordLayout.setError(getString(R.string.error_password_weak)); ok = false; }
        if (!pw.equals(confirm)) { b.confirmLayout.setError(getString(R.string.error_password_mismatch)); ok = false; }
        if (!b.acceptTerms.isChecked()) { snack(getString(R.string.error_accept_terms)); ok = false; }
        if (!ok) return;

        Ui.hideKeyboard(b.getRoot());
        Ui.loading(b.submit, true);
        Map<String, Object> body = Json.map("name", name, "email", email, "phone", phone, "password", pw,
                "governorate", gov, "syndicateNumber", syndicate, "degree", degree,
                "specialization", Texts.nullIfBlank(b.specialization.getText()),
                "acceptTerms", true, "deviceId", Session.get().deviceId(), "deviceName", Build.MANUFACTURER + " " + Build.MODEL);
        call(ApiClient.api().register(body), res -> {
            if (res == null || res.accessToken == null) {
                Ui.loading(b.submit, false);
                return;
            }
            Session.get().saveTokens(res.accessToken, res.refreshToken);
            LoginActivity.onAuthenticated(this, () -> Ui.loading(b.submit, false));
        }, e -> {
            Ui.loading(b.submit, false);
            if (!e.fieldErrors.isEmpty()) {
                setFieldError(b.nameLayout, e.fieldErrors.get("name"));
                setFieldError(b.emailLayout, e.fieldErrors.get("email"));
                setFieldError(b.phoneLayout, e.fieldErrors.get("phone"));
                setFieldError(b.syndicateLayout, e.fieldErrors.get("syndicateNumber"));
                setFieldError(b.passwordLayout, e.fieldErrors.get("password"));
            } else {
                showError(e);
            }
        });
    }

    private static void setFieldError(TextInputLayout l, @Nullable String msg) {
        if (msg != null) l.setError(msg);
    }
}
