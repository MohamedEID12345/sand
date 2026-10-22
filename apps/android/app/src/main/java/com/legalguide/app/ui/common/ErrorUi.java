package com.legalguide.app.ui.common;

import android.content.Context;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.legalguide.app.R;
import com.legalguide.app.core.net.ApiError;
import com.legalguide.app.core.util.Texts;

/** Turns API errors into the right UI: paywall, maintenance dialog or snackbar. */
public final class ErrorUi {
    private ErrorUi() {}

    public static String message(Context c, ApiError e) {
        boolean transport = e.is(ApiError.NETWORK) || e.is(ApiError.TIMEOUT) || e.is(ApiError.UNKNOWN);
        if (!transport && !Texts.blank(e.message)) return e.message;
        switch (e.code) {
            case ApiError.NETWORK: return c.getString(R.string.error_network);
            case ApiError.TIMEOUT: return c.getString(R.string.error_timeout);
            case ApiError.FORBIDDEN: return c.getString(R.string.error_forbidden);
            case ApiError.NOT_FOUND: return c.getString(R.string.error_not_found);
            case ApiError.RATE_LIMITED: return c.getString(R.string.error_rate_limited);
            case ApiError.DAILY_LIMIT: return c.getString(R.string.error_daily_limit);
            case ApiError.FREE_USES_EXHAUSTED: return c.getString(R.string.paywall_free_exhausted);
            case ApiError.INSUFFICIENT_CREDITS: return c.getString(R.string.paywall_no_credits);
            case ApiError.PLAN_LIMIT: return c.getString(R.string.paywall_plan_limit);
            case ApiError.PAYMENT_REQUIRED: return c.getString(R.string.paywall_generic);
            case ApiError.FEATURE_DISABLED: return c.getString(R.string.error_feature_disabled);
            case ApiError.MAINTENANCE: return c.getString(R.string.maintenance_message);
            case ApiError.FILE_TOO_LARGE: return c.getString(R.string.error_file_too_large);
            case ApiError.VALIDATION: return c.getString(R.string.error_validation);
            case ApiError.SERVER: return c.getString(R.string.error_server);
            default: return c.getString(R.string.error_generic);
        }
    }

    public static void show(@Nullable FragmentActivity a, ApiError e) {
        if (a == null || a.isFinishing()) return;
        if (e.is(ApiError.UNAUTHORIZED)) return; // session layer routes to login
        if (e.isPaywall()) {
            PaywallSheet.show(a, e.code, message(a, e));
            return;
        }
        if (e.is(ApiError.MAINTENANCE)) {
            new MaterialAlertDialogBuilder(a)
                    .setTitle(R.string.maintenance_title)
                    .setMessage(message(a, e))
                    .setPositiveButton(R.string.action_ok, null)
                    .show();
            return;
        }
        snack(a, message(a, e));
    }

    public static void snack(@Nullable FragmentActivity a, CharSequence msg) {
        if (a == null) return;
        View root = a.findViewById(android.R.id.content);
        if (root != null) Snackbar.make(root, msg, Snackbar.LENGTH_LONG).show();
    }
}
