package com.legalguide.app.core.util;

import android.os.Handler;
import android.os.Looper;

/** Runs the last submitted action after a quiet period (search-as-you-type). */
public final class Debouncer {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final long delayMs;
    private Runnable pending;

    public Debouncer(long delayMs) {
        this.delayMs = delayMs;
    }

    public void submit(Runnable r) {
        cancel();
        pending = r;
        handler.postDelayed(r, delayMs);
    }

    public void cancel() {
        if (pending != null) handler.removeCallbacks(pending);
        pending = null;
    }
}
