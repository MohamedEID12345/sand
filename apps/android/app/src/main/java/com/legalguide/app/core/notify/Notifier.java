package com.legalguide.app.core.notify;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import com.legalguide.app.R;
import com.legalguide.app.ui.main.MainActivity;

/** System notification channels and posting. */
public final class Notifier {
    public static final String CH_HEARINGS = "hearings";
    public static final String CH_ACCOUNT = "account";
    public static final String CH_UPDATES = "updates";

    private Notifier() {}

    public static void createChannels(Context ctx) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager nm = ctx.getSystemService(NotificationManager.class);
        if (nm == null) return;
        NotificationChannel h = new NotificationChannel(CH_HEARINGS, ctx.getString(R.string.channel_hearings), NotificationManager.IMPORTANCE_HIGH);
        h.setDescription(ctx.getString(R.string.channel_hearings_desc));
        NotificationChannel a = new NotificationChannel(CH_ACCOUNT, ctx.getString(R.string.channel_account), NotificationManager.IMPORTANCE_DEFAULT);
        a.setDescription(ctx.getString(R.string.channel_account_desc));
        NotificationChannel u = new NotificationChannel(CH_UPDATES, ctx.getString(R.string.channel_updates), NotificationManager.IMPORTANCE_DEFAULT);
        u.setDescription(ctx.getString(R.string.channel_updates_desc));
        nm.createNotificationChannel(h);
        nm.createNotificationChannel(a);
        nm.createNotificationChannel(u);
    }

    public static boolean canPost(Context ctx) {
        if (Build.VERSION.SDK_INT >= 33
                && ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return false;
        }
        return NotificationManagerCompat.from(ctx).areNotificationsEnabled();
    }

    public static String channelFor(@Nullable String type) {
        if (type == null) return CH_UPDATES;
        switch (type) {
            case "HEARING":
            case "DEADLINE":
                return CH_HEARINGS;
            case "PAYMENT_SUCCESS":
            case "PAYMENT_FAILED":
            case "SUBSCRIPTION_EXPIRING":
            case "CREDITS_LOW":
                return CH_ACCOUNT;
            default:
                return CH_UPDATES;
        }
    }

    @SuppressWarnings("MissingPermission")
    public static void show(Context ctx, int id, String channel, String title, @Nullable String body,
                            @Nullable String refType, @Nullable String refId) {
        if (!canPost(ctx)) return;
        Intent open = new Intent(ctx, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(MainActivity.EXTRA_REF_TYPE, refType)
                .putExtra(MainActivity.EXTRA_REF_ID, refId);
        PendingIntent pi = PendingIntent.getActivity(ctx, id, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        NotificationCompat.Builder b = new NotificationCompat.Builder(ctx, channel)
                .setSmallIcon(R.drawable.ic_scale)
                .setColor(ContextCompat.getColor(ctx, R.color.brand_navy))
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                .setAutoCancel(true)
                .setContentIntent(pi)
                .setPriority(CH_HEARINGS.equals(channel) ? NotificationCompat.PRIORITY_HIGH : NotificationCompat.PRIORITY_DEFAULT);
        NotificationManagerCompat.from(ctx).notify(id, b.build());
    }
}
