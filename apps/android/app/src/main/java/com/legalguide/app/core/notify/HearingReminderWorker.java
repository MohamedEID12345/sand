package com.legalguide.app.core.notify;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.legalguide.app.R;
import com.legalguide.app.core.session.Prefs;
import com.legalguide.app.core.session.Session;

/** Local reminder for an upcoming hearing (scheduled by {@link ReminderScheduler}). */
public class HearingReminderWorker extends Worker {
    static final String K_ID = "id", K_CASE = "case", K_COURT = "court", K_WHEN = "when", K_CASE_ID = "caseId";

    public HearingReminderWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        Session.init(getApplicationContext());
        if (!Prefs.remindersEnabled() || !Session.get().isLoggedIn()) return Result.success();
        String id = getInputData().getString(K_ID);
        String title = getApplicationContext().getString(R.string.reminder_hearing_title, getInputData().getString(K_CASE));
        String body = getApplicationContext().getString(R.string.reminder_hearing_body,
                getInputData().getString(K_WHEN), getInputData().getString(K_COURT));
        Notifier.show(getApplicationContext(), ("h" + id).hashCode(), Notifier.CH_HEARINGS, title, body,
                "CASE", getInputData().getString(K_CASE_ID));
        return Result.success();
    }
}
