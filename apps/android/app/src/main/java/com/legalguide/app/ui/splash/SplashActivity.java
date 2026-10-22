package com.legalguide.app.ui.splash;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.core.splashscreen.SplashScreen;

import com.legalguide.app.BuildConfig;
import com.legalguide.app.R;
import com.legalguide.app.core.net.ApiClient;
import com.legalguide.app.core.net.ApiError;
import com.legalguide.app.core.session.Prefs;
import com.legalguide.app.core.session.Session;
import com.legalguide.app.core.util.Intents;
import com.legalguide.app.core.util.Texts;
import com.legalguide.app.data.model.AppConfig;
import com.legalguide.app.databinding.ActivityBlockingBinding;
import com.legalguide.app.ui.auth.LoginActivity;
import com.legalguide.app.ui.common.BaseActivity;
import com.legalguide.app.ui.common.ErrorUi;
import com.legalguide.app.ui.main.MainActivity;

/** Entry point: platform config → maintenance / forced update → onboarding → login → bootstrap → main. */
public class SplashActivity extends BaseActivity {
    private boolean keep = true;
    @Nullable private ActivityBlockingBinding blocking;

    @Override
    protected boolean requiresAuth() {
        return false;
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        SplashScreen splash = SplashScreen.installSplashScreen(this);
        super.onCreate(savedInstanceState);
        splash.setKeepOnScreenCondition(() -> keep);
        loadConfig();
    }

    private void loadConfig() {
        call(ApiClient.api().config(), cfg -> {
            Session.get().setConfig(cfg);
            if (cfg != null && cfg.maintenanceMode) {
                showBlocking(R.drawable.ic_settings, getString(R.string.maintenance_title),
                        Texts.blank(cfg.maintenanceMessage) ? getString(R.string.maintenance_message) : cfg.maintenanceMessage,
                        getString(R.string.action_retry), v -> reload());
            } else if (cfg != null && BuildConfig.VERSION_CODE < cfg.minAndroidVersionCode) {
                showBlocking(R.drawable.ic_download, getString(R.string.update_required_title), getString(R.string.update_required_message),
                        getString(R.string.action_update), v -> Intents.openUrl(this,
                                Texts.blank(cfg.updateUrl) ? "https://play.google.com/store/apps/details?id=" + getPackageName() : cfg.updateUrl));
            } else {
                next();
            }
        }, e -> {
            AppConfig cached = Session.get().config();
            if (cached != null || Session.get().isLoggedIn()) next();
            else showError(e);
        });
    }

    private void next() {
        if (!Prefs.onboardingDone()) {
            go(new Intent(this, OnboardingActivity.class));
        } else if (!Session.get().isLoggedIn()) {
            go(new Intent(this, LoginActivity.class));
        } else {
            call(ApiClient.api().bootstrap(), b -> {
                Session.get().setBootstrap(b);
                go(mainIntent());
            }, e -> {
                if (e.is(ApiError.UNAUTHORIZED)) {
                    go(new Intent(this, LoginActivity.class));
                } else if (Session.get().current() != null) {
                    go(mainIntent()); // offline: use cached state
                } else {
                    showError(e);
                }
            });
        }
    }

    private Intent mainIntent() {
        Intent i = new Intent(this, MainActivity.class);
        if (getIntent() != null && getIntent().getExtras() != null) i.putExtras(getIntent().getExtras());
        return i;
    }

    @Override
    public void showError(ApiError e) {
        showBlocking(e.is(ApiError.NETWORK) || e.is(ApiError.TIMEOUT) ? R.drawable.ic_cloud_off : R.drawable.ic_error,
                getString(e.is(ApiError.NETWORK) ? R.string.state_offline_title : R.string.state_error_title),
                ErrorUi.message(this, e), getString(R.string.action_retry), v -> reload());
    }

    private void reload() {
        if (blocking != null) {
            blocking.blockingProgress.setVisibility(View.VISIBLE);
            blocking.blockingAction.setEnabled(false);
        }
        loadConfig();
    }

    private void showBlocking(int icon, String title, String message, String action, View.OnClickListener onAction) {
        keep = false;
        if (blocking == null) {
            blocking = ActivityBlockingBinding.inflate(getLayoutInflater());
            setContentView(blocking.getRoot());
            applyInsets(blocking.getRoot());
        }
        blocking.blockingProgress.setVisibility(View.GONE);
        blocking.blockingAction.setEnabled(true);
        blocking.blockingIcon.setImageResource(icon);
        blocking.blockingTitle.setText(title);
        blocking.blockingMessage.setText(message);
        blocking.blockingAction.setText(action);
        blocking.blockingAction.setOnClickListener(onAction);
        AppConfig cfg = Session.get().config();
        boolean hasSupport = cfg != null && !Texts.blank(cfg.supportWhatsapp);
        blocking.blockingSupport.setVisibility(hasSupport ? View.VISIBLE : View.GONE);
        if (hasSupport) blocking.blockingSupport.setOnClickListener(v -> Intents.whatsapp(this, cfg.supportWhatsapp, null));
    }

    private void go(Intent i) {
        keep = false;
        startActivity(i);
        finish();
    }
}
