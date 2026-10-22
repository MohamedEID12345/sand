package com.legalguide.app.ui.common;

import android.content.Context;

import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.legalguide.app.R;

public final class Dialogs {
    private Dialogs() {}

    public static void confirm(Context c, CharSequence title, @Nullable CharSequence message,
                               @StringRes int positive, Runnable onConfirm) {
        new MaterialAlertDialogBuilder(c)
                .setTitle(title)
                .setMessage(message)
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(positive, (d, w) -> onConfirm.run())
                .show();
    }

    public static void confirmDelete(Context c, CharSequence what, Runnable onConfirm) {
        confirm(c, c.getString(R.string.confirm_delete_title, what), c.getString(R.string.confirm_delete_message),
                R.string.action_delete, onConfirm);
    }

    /** Confirms an AI action and shows its credit cost (or the free-use it will consume). */
    public static void confirmCost(Context c, CharSequence action, int credits, Runnable onConfirm) {
        String msg = credits > 0 ? c.getString(R.string.confirm_cost_message, credits) : c.getString(R.string.confirm_cost_unknown);
        confirm(c, action, msg, R.string.action_continue, onConfirm);
    }

    public static void info(Context c, CharSequence title, CharSequence message) {
        new MaterialAlertDialogBuilder(c).setTitle(title).setMessage(message).setPositiveButton(R.string.action_ok, null).show();
    }
}
