package com.legalguide.app.core.util;

import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.browser.customtabs.CustomTabColorSchemeParams;
import androidx.browser.customtabs.CustomTabsIntent;
import androidx.core.content.ContextCompat;

import com.legalguide.app.R;

/** Outbound intents: browser tabs, phone, WhatsApp, email, share, clipboard. */
public final class Intents {
    private Intents() {}

    public static void openUrl(Context ctx, @Nullable String url) {
        if (Texts.blank(url)) return;
        Uri uri = Uri.parse(url);
        try {
            CustomTabsIntent tab = new CustomTabsIntent.Builder()
                    .setDefaultColorSchemeParams(new CustomTabColorSchemeParams.Builder()
                            .setToolbarColor(ContextCompat.getColor(ctx, R.color.brand_navy))
                            .build())
                    .setShowTitle(true)
                    .build();
            tab.launchUrl(ctx, uri);
        } catch (ActivityNotFoundException e) {
            safeStart(ctx, new Intent(Intent.ACTION_VIEW, uri));
        }
    }

    public static void dial(Context ctx, @Nullable String phone) {
        if (Texts.blank(phone)) return;
        safeStart(ctx, new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + phone.trim())));
    }

    public static void whatsapp(Context ctx, @Nullable String phone, @Nullable String message) {
        if (Texts.blank(phone)) return;
        String digits = Texts.asciiDigits(phone).replaceAll("[^0-9]", "");
        if (digits.startsWith("0")) digits = "2" + digits; // local Egyptian number → 20...
        String url = "https://wa.me/" + digits + (Texts.blank(message) ? "" : "?text=" + Uri.encode(message));
        safeStart(ctx, new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
    }

    public static void email(Context ctx, @Nullable String to, @Nullable String subject) {
        if (Texts.blank(to)) return;
        Intent i = new Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:" + to.trim()));
        if (subject != null) i.putExtra(Intent.EXTRA_SUBJECT, subject);
        safeStart(ctx, i);
    }

    public static void shareText(Context ctx, @Nullable String title, String text) {
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType("text/plain");
        if (title != null) i.putExtra(Intent.EXTRA_SUBJECT, title);
        i.putExtra(Intent.EXTRA_TEXT, text);
        safeStart(ctx, Intent.createChooser(i, ctx.getString(R.string.action_share)));
    }

    public static void copy(Context ctx, String text) {
        ClipboardManager cm = (ClipboardManager) ctx.getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText("legal-guide", text));
            Toast.makeText(ctx, R.string.copied, Toast.LENGTH_SHORT).show();
        }
    }

    private static void safeStart(Context ctx, Intent i) {
        try {
            if (!(ctx instanceof android.app.Activity)) i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(i);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(ctx, R.string.error_no_app, Toast.LENGTH_SHORT).show();
        }
    }
}
