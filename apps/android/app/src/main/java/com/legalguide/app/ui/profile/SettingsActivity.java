package com.legalguide.app.ui.profile;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.fragment.app.FragmentActivity;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.legalguide.app.BuildConfig;
import com.legalguide.app.R;
import com.legalguide.app.core.net.ApiClient;
import com.legalguide.app.core.net.Calls;
import com.legalguide.app.core.notify.ReminderScheduler;
import com.legalguide.app.core.session.Prefs;
import com.legalguide.app.core.session.Session;
import com.legalguide.app.core.util.Intents;
import com.legalguide.app.core.util.Json;
import com.legalguide.app.core.util.Texts;
import com.legalguide.app.data.model.AppConfig;
import com.legalguide.app.databinding.ActivitySettingsBinding;
import com.legalguide.app.databinding.DialogInputBinding;
import com.legalguide.app.ui.auth.LoginActivity;
import com.legalguide.app.ui.common.BaseActivity;
import com.legalguide.app.ui.common.Dialogs;
import com.legalguide.app.ui.common.Ui;

public class SettingsActivity extends BaseActivity {
    private ActivitySettingsBinding b;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!ensureSession()) return;
        b = ActivitySettingsBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        applyInsets(b.getRoot());
        setupToolbar(b.appBar.toolbar, R.string.settings_title, true);

        int mode = Prefs.themeMode();
        b.themeGroup.check(mode == AppCompatDelegate.MODE_NIGHT_NO ? R.id.theme_light
                : mode == AppCompatDelegate.MODE_NIGHT_YES ? R.id.theme_dark : R.id.theme_system);
        b.themeGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) return;
            int m = checkedId == R.id.theme_light ? AppCompatDelegate.MODE_NIGHT_NO
                    : checkedId == R.id.theme_dark ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
            Prefs.setThemeMode(m);
            AppCompatDelegate.setDefaultNightMode(m);
        });

        b.notifications.setChecked(Prefs.notificationsEnabled());
        b.notifications.setOnCheckedChangeListener((v, on) -> {
            Prefs.setNotificationsEnabled(on);
            if (on) ReminderScheduler.startSync(this);
            else ReminderScheduler.stopSync(this);
        });
        b.reminders.setChecked(Prefs.remindersEnabled());
        b.reminders.setOnCheckedChangeListener((v, on) -> {
            Prefs.setRemindersEnabled(on);
            if (!on) ReminderScheduler.cancelAllReminders(this);
        });

        AppConfig cfg = Session.get().config();
        b.privacy.setOnClickListener(v -> { if (cfg != null) Intents.openUrl(this, cfg.privacyUrl); });
        b.terms.setOnClickListener(v -> { if (cfg != null) Intents.openUrl(this, cfg.termsUrl); });
        b.website.setOnClickListener(v -> { if (cfg != null) Intents.openUrl(this, cfg.websiteUrl); });
        Ui.show(b.website, cfg != null && !Texts.blank(cfg.websiteUrl));
        // Office-private templates are managed on the web portal served by the API host: <host>/office/
        b.officePortal.setOnClickListener(v -> Intents.openUrl(this, BuildConfig.API_BASE_URL.replaceFirst("api/v1/?$", "") + "office/"));
        b.version.setText(getString(R.string.more_version, BuildConfig.VERSION_NAME + " (" + BuildConfig.VERSION_CODE + ")"));
        b.deleteAccount.setOnClickListener(v -> requestDeletion());
        b.logout.setOnClickListener(v -> confirmLogout(this));
    }

    private void requestDeletion() {
        DialogInputBinding d = DialogInputBinding.inflate(LayoutInflater.from(this));
        d.inputLayout.setHint(getString(R.string.delete_account_reason));
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.delete_account_title)
                .setMessage(R.string.delete_account_message)
                .setView(d.getRoot())
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.delete_account_confirm, (dlg, w) ->
                        call(ApiClient.api().requestDeletion(Json.map("reason", Texts.nullIfBlank(d.input.getText()))), ok ->
                                Dialogs.info(this, getString(R.string.delete_account_title), getString(R.string.delete_account_sent))))
                .show();
    }

    /** Revokes the refresh token server-side (best effort) and clears local credentials. */
    public static void confirmLogout(FragmentActivity a) {
        Dialogs.confirm(a, a.getString(R.string.action_logout), a.getString(R.string.logout_confirm), R.string.action_logout, () -> {
            String refresh = Session.get().refreshToken();
            Calls.enqueue(null, ApiClient.api().logout(Json.map("refreshToken", refresh, "deviceId", Session.get().deviceId())), ok -> { }, e -> { });
            ReminderScheduler.stopSync(a);
            ReminderScheduler.cancelAllReminders(a);
            Session.get().clearLocal();
            a.startActivity(new Intent(a, LoginActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
            a.finish();
        });
    }
}
