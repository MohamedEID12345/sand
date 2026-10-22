package com.legalguide.app.ui.auth;

import android.os.Bundle;
import android.view.View;

import androidx.annotation.Nullable;

import com.legalguide.app.R;
import com.legalguide.app.core.net.ApiClient;
import com.legalguide.app.core.util.FieldValidator;
import com.legalguide.app.core.util.Json;
import com.legalguide.app.core.util.Texts;
import com.legalguide.app.databinding.ActivityForgotPasswordBinding;
import com.legalguide.app.ui.common.BaseActivity;
import com.legalguide.app.ui.common.Ui;

/** Two steps: request a reset code by email, then set a new password with the code. */
public class ForgotPasswordActivity extends BaseActivity {
    private ActivityForgotPasswordBinding b;
    private String email;

    @Override
    protected boolean requiresAuth() {
        return false;
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityForgotPasswordBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        applyInsets(b.getRoot());
        setupToolbar(b.appBar.toolbar, R.string.forgot_title, true);
        b.send.setOnClickListener(v -> requestCode());
        b.reset.setOnClickListener(v -> reset());
        b.resend.setOnClickListener(v -> requestCode());
    }

    private void requestCode() {
        b.emailLayout.setError(null);
        String e = Texts.trim(b.email.getText());
        if (!FieldValidator.isEmail(e)) {
            b.emailLayout.setError(getString(R.string.error_email));
            return;
        }
        email = e;
        Ui.loading(b.send, true);
        call(ApiClient.api().forgotPassword(Json.map("email", e)), ok -> {
            Ui.loading(b.send, false);
            b.stepEmail.setVisibility(View.GONE);
            b.stepReset.setVisibility(View.VISIBLE);
            b.resetHint.setText(getString(R.string.forgot_code_sent, e));
        }, err -> {
            Ui.loading(b.send, false);
            showError(err);
        });
    }

    private void reset() {
        b.codeLayout.setError(null);
        b.newPasswordLayout.setError(null);
        String code = Texts.asciiDigits(Texts.trim(b.code.getText()));
        String pw = Texts.trim(b.newPassword.getText());
        if (code.length() < 4) {
            b.codeLayout.setError(getString(R.string.error_required));
            return;
        }
        if (!FieldValidator.isStrongPassword(pw)) {
            b.newPasswordLayout.setError(getString(R.string.error_password_weak));
            return;
        }
        Ui.loading(b.reset, true);
        call(ApiClient.api().resetPassword(Json.map("email", email, "code", code, "password", pw)), ok -> {
            Ui.loading(b.reset, false);
            toast(R.string.forgot_done);
            finish();
        }, err -> {
            Ui.loading(b.reset, false);
            if (err.fieldErrors.containsKey("code")) b.codeLayout.setError(err.fieldErrors.get("code"));
            else showError(err);
        });
    }
}
