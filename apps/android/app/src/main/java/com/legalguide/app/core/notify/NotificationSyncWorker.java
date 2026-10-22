package com.legalguide.app.core.notify;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.legalguide.app.core.net.ApiClient;
import com.legalguide.app.core.session.Prefs;
import com.legalguide.app.core.session.Session;
import com.legalguide.app.data.model.AppNotification;
import com.legalguide.app.data.model.Page;

import java.io.IOException;
import java.time.Instant;

import retrofit2.Response;

/**
 * Pulls new in-app notifications from the backend and mirrors them as system notifications.
 * (No FCM dependency: notifications are polled from the API.)
 */
public class NotificationSyncWorker extends Worker {

    public NotificationSyncWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context ctx = getApplicationContext();
        Session.init(ctx);
        Prefs.init(ctx);
        ApiClient.init(ctx);
        if (!Session.get().isLoggedIn() || !Prefs.notificationsEnabled()) return Result.success();
        String since = Prefs.lastNotificationSync();
        String now = Instant.now().toString();
        try {
            Response<Page<AppNotification>> r = ApiClient.api().notifications(null, since).execute();
            if (!r.isSuccessful() || r.body() == null) return r.code() >= 500 ? Result.retry() : Result.success();
            int unread = 0;
            if (r.body().items != null) {
                for (AppNotification n : r.body().items) {
                    if (n.read) continue;
                    unread++;
                    if (since != null) { // first sync only records the watermark, it doesn't flood the tray
                        Notifier.show(ctx, ("n" + n.id).hashCode(), Notifier.channelFor(n.type), n.title, n.body, n.refType, n.refId);
                    }
                }
            }
            Prefs.setLastNotificationSync(now);
            if (unread > 0) {
                Response<com.legalguide.app.data.model.Count> c = ApiClient.api().unreadCount().execute();
                if (c.isSuccessful() && c.body() != null) Session.get().setUnread(c.body().count);
            }
            return Result.success();
        } catch (IOException e) {
            return Result.retry();
        }
    }
}
