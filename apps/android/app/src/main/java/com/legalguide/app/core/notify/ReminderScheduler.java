package com.legalguide.app.core.notify;

import android.content.Context;

import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import com.legalguide.app.core.session.Prefs;
import com.legalguide.app.core.util.Dates;
import com.legalguide.app.core.util.Texts;
import com.legalguide.app.data.model.Hearing;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Schedules local hearing reminders (24h and 2h before) and the periodic notification sync. */
public final class ReminderScheduler {
    private static final String SYNC_WORK = "notification-sync";

    private ReminderScheduler() {}

    public static void scheduleAll(Context ctx, List<Hearing> hearings) {
        if (hearings == null) return;
        for (Hearing h : hearings) schedule(ctx, h);
    }

    public static void schedule(Context ctx, Hearing h) {
        if (h == null || h.id == null || !Prefs.remindersEnabled()) return;
        ZonedDateTime when = Dates.parse(h.hearingDate);
        if (when == null) return;
        if (Dates.isDateOnly(h.hearingDate)) when = when.withHour(9);
        enqueue(ctx, h, when.minusHours(24), "24");
        enqueue(ctx, h, when.minusHours(2), "2");
    }

    private static void enqueue(Context ctx, Hearing h, ZonedDateTime fireAt, String tag) {
        long delay = Duration.between(ZonedDateTime.now(Dates.zone()), fireAt).toMillis();
        String name = "hearing-" + h.id + "-" + tag;
        if (delay <= 0) {
            WorkManager.getInstance(ctx).cancelUniqueWork(name);
            return;
        }
        Data data = new Data.Builder()
                .putString(HearingReminderWorker.K_ID, h.id + "-" + tag)
                .putString(HearingReminderWorker.K_CASE, Texts.orDash(Texts.blank(h.caseTitle) ? h.caseNumber : h.caseTitle))
                .putString(HearingReminderWorker.K_COURT, Texts.joinNonBlank(" • ", h.court, h.circuit))
                .putString(HearingReminderWorker.K_WHEN, Dates.dayTime(h.hearingDate))
                .putString(HearingReminderWorker.K_CASE_ID, h.caseId)
                .build();
        OneTimeWorkRequest req = new OneTimeWorkRequest.Builder(HearingReminderWorker.class)
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .setInputData(data)
                .addTag("hearing-reminder")
                .build();
        WorkManager.getInstance(ctx).enqueueUniqueWork(name, ExistingWorkPolicy.REPLACE, req);
    }

    public static void cancel(Context ctx, String hearingId) {
        WorkManager.getInstance(ctx).cancelUniqueWork("hearing-" + hearingId + "-24");
        WorkManager.getInstance(ctx).cancelUniqueWork("hearing-" + hearingId + "-2");
    }

    public static void cancelAllReminders(Context ctx) {
        WorkManager.getInstance(ctx).cancelAllWorkByTag("hearing-reminder");
    }

    public static void startSync(Context ctx) {
        if (!Prefs.notificationsEnabled()) {
            stopSync(ctx);
            return;
        }
        PeriodicWorkRequest req = new PeriodicWorkRequest.Builder(NotificationSyncWorker.class, 30, TimeUnit.MINUTES)
                .setConstraints(new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build();
        WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(SYNC_WORK, ExistingPeriodicWorkPolicy.KEEP, req);
    }

    public static void stopSync(Context ctx) {
        WorkManager.getInstance(ctx).cancelUniqueWork(SYNC_WORK);
    }
}
