package com.legalguide.app.ui.auth;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.inputmethod.EditorInfo;

import androidx.annotation.Nullable;

import com.legalguide.app.R;
import com.legalguide.app.core.net.ApiClient;
import com.legalguide.app.core.net.ApiError;
import com.legalguide.app.core.notify.ReminderScheduler;
import com.legalguide.app.core.session.Session;
import com.legalguide.app.core.util.FieldValidator;
import com.legalguide.app.core.util.Intents;
import com.legalguide.app.core.util.Json;
import com.legalguide.app.core.util.Texts;
import com.legalguide.app.data.model.AppConfig;
import com.legalguide.app.databinding.ActivityLoginBinding;
import com.legalguide.app.ui.common.BaseActivity;
import com.legalguide.app.ui.common.Ui;
import com.legalguide.app.ui.main.MainActivity;

public class LoginActivity extends BaseActivity {
    private ActivityLoginBinding b;

    @Override
    protected boolean requiresAuth() {
        return false;
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityLoginBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        applyInsets(b.getRoot());

        b.password.setOnEditorActionListener((v, id, e) -> {
            if (id == EditorInfo.IME_ACTION_DONE) {
                submit();
                return true;
            }
            return false;
        });
        b.login.setOnClickListener(v -> submit());
        b.forgot.setOnClickListener(v -> startActivity(new Intent(this, ForgotPasswordActivity.class)));
        b.register.setOnClickListener(v -> startActivity(new Intent(this, RegisterActivity.class)));
        AppConfig cfg = Session.get().config();
        b.terms.setOnClickListener(v -> {
            if (cfg != null) Intents.openUrl(this, cfg.termsUrl);
        });
    }

    private void submit() {
        b.identifierLayout.setError(null);
        b.passwordLayout.setError(null);
        String id = Texts.asciiDigits(Texts.trim(b.identifier.getText()));
        String pw = Texts.trim(b.password.getText());
        boolean ok = true;
        if (id.isEmpty()) {
            b.identifierLayout.setError(getString(R.string.error_required));
            ok = false;
        } else if (!FieldValidator.isEmail(id) && !FieldValidator.isEgyptianMobile(id)) {
            b.identifierLayout.setError(getString(R.string.login_identifier_invalid));
            ok = false;
        }
        if (pw.isEmpty()) {
            b.passwordLayout.setError(getString(R.string.error_required));
            ok = false;
        }
        if (!ok) return;
        Ui.hideKeyboard(b.getRoot());
        Ui.loading(b.login, true);
        call(ApiClient.api().login(Json.map("identifier", id, "password", pw,
                "deviceId", Session.get().deviceId(), "deviceName", Build.MANUFACTURER + " " + Build.MODEL)), res -> {
            if (res == null || res.accessToken == null) {
                Ui.loading(b.login, false);
                return;
            }
            Session.get().saveTokens(res.accessToken, res.refreshToken);
            onAuthenticated(this, () -> Ui.loading(b.login, false));
        }, e -> {
            Ui.loading(b.login, false);
            if (e.is(ApiError.UNAUTHORIZED) || e.is(ApiError.VALIDATION)) {
                b.passwordLayout.setError(Texts.blank(e.message) ? getString(R.string.login_invalid) : e.message);
            } else {
                showError(e);
            }
        });
    }

    /** Loads the bootstrap then opens the main screen (shared with registration). */
    static void onAuthenticated(BaseActivity a, Runnable onFail) {
        com.legalguide.app.core.net.Calls.enqueue(a, ApiClient.api().bootstrap(), boot -> {
            Session.get().setBootstrap(boot);
            ReminderScheduler.startSync(a);
            a.startActivity(new Intent(a, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
            a.finish();
        }, e -> {
            onFail.run();
            a.showError(e);
        });
    }
}
