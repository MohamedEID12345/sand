package com.legalguide.app;

import android.app.Application;
import android.os.Build;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;

import com.legalguide.app.core.net.ApiClient;
import com.legalguide.app.core.notify.Notifier;
import com.legalguide.app.core.notify.ReminderScheduler;
import com.legalguide.app.core.session.Prefs;
import com.legalguide.app.core.session.Session;

public class LegalGuideApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        Prefs.init(this);
        Session.init(this);
        ApiClient.init(this);

        AppCompatDelegate.setDefaultNightMode(Prefs.themeMode());
        // Arabic-first, RTL: force the app locale unless the user picked one in system settings (API 33+).
        if (Build.VERSION.SDK_INT < 33 || AppCompatDelegate.getApplicationLocales().isEmpty()) {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("ar"));
        }

        Notifier.createChannels(this);
        if (Session.get().isLoggedIn()) ReminderScheduler.startSync(this);
    }
}
