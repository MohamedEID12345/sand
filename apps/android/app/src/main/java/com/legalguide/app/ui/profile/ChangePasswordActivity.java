package com.legalguide.app.ui.profile;

import android.os.Bundle;

import androidx.annotation.Nullable;

import com.legalguide.app.R;
import com.legalguide.app.core.net.ApiClient;
import com.legalguide.app.core.session.Session;
import com.legalguide.app.core.util.FieldValidator;
import com.legalguide.app.core.util.Json;
import com.legalguide.app.core.util.Texts;
import com.legalguide.app.databinding.ActivityChangePasswordBinding;
import com.legalguide.app.ui.common.BaseActivity;
import com.legalguide.app.ui.common.Ui;

public class ChangePasswordActivity extends BaseActivity {
    private ActivityChangePasswordBinding b;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!ensureSession()) return;
        b = ActivityChangePasswordBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        applyInsets(b.getRoot());
        setupToolbar(b.appBar.toolbar, R.string.change_password, true);
        b.save.setOnClickListener(v -> save());
    }

    private void save() {
        b.currentLayout.setError(null);
        b.newPasswordLayout.setError(null);
        b.confirmLayout.setError(null);
        String current = Texts.trim(b.current.getText());
        String pw = Texts.trim(b.newPassword.getText());
        String confirm = Texts.trim(b.confirm.getText());
        boolean ok = true;
        if (current.isEmpty()) { b.currentLayout.setError(getString(R.string.error_required)); ok = false; }
        if (!FieldValidator.isStrongPassword(pw)) { b.newPasswordLayout.setError(getString(R.string.error_password_weak)); ok = false; }
        if (!pw.equals(confirm)) { b.confirmLayout.setError(getString(R.string.error_password_mismatch)); ok = false; }
        if (!ok) return;
        Ui.loading(b.save, true);
        call(ApiClient.api().changePassword(Json.map("currentPassword", current, "newPassword", pw,
                "deviceId", Session.get().deviceId())), r -> {
            Ui.loading(b.save, false);
            toast(R.string.password_changed);
            finish();
        }, e -> {
            Ui.loading(b.save, false);
            if (e.fieldErrors.containsKey("currentPassword")) b.currentLayout.setError(e.fieldErrors.get("currentPassword"));
            else showError(e);
        });
    }
}
