package com.legalguide.app.core.session;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.appcompat.app.AppCompatDelegate;

/** Non-sensitive UI preferences. */
public final class Prefs {
    private static SharedPreferences sp;

    private Prefs() {}

    public static void init(Context ctx) {
        sp = ctx.getApplicationContext().getSharedPreferences("lg_prefs", Context.MODE_PRIVATE);
    }

    public static boolean onboardingDone() { return sp.getBoolean("onboarding_done", false); }
    public static void setOnboardingDone() { sp.edit().putBoolean("onboarding_done", true).apply(); }

    /** One of AppCompatDelegate.MODE_NIGHT_* */
    public static int themeMode() { return sp.getInt("theme_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM); }
    public static void setThemeMode(int mode) { sp.edit().putInt("theme_mode", mode).apply(); }

    public static boolean notificationsEnabled() { return sp.getBoolean("notif_enabled", true); }
    public static void setNotificationsEnabled(boolean v) { sp.edit().putBoolean("notif_enabled", v).apply(); }

    public static boolean remindersEnabled() { return sp.getBoolean("reminders_enabled", true); }
    public static void setRemindersEnabled(boolean v) { sp.edit().putBoolean("reminders_enabled", v).apply(); }

    public static String lastNotificationSync() { return sp.getString("notif_last_sync", null); }
    public static void setLastNotificationSync(String iso) { sp.edit().putString("notif_last_sync", iso).apply(); }

    public static boolean notifPermissionAsked() { return sp.getBoolean("notif_perm_asked", false); }
    public static void setNotifPermissionAsked() { sp.edit().putBoolean("notif_perm_asked", true).apply(); }

    public static void clearUserScoped() {
        sp.edit().remove("notif_last_sync").apply();
    }
}
