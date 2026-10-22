package com.legalguide.app.ui.common;

import android.content.res.ColorStateList;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.ColorRes;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.widget.ImageViewCompat;

import com.legalguide.app.R;

/** Semantic color tones for badges, icon tiles and status strips. */
public enum Tone {
    NEUTRAL(R.color.tone_neutral, R.color.tone_neutral_bg),
    PRIMARY(R.color.lg_on_primary_container, R.color.lg_primary_container),
    SUCCESS(R.color.tone_success, R.color.tone_success_bg),
    WARNING(R.color.tone_warning, R.color.tone_warning_bg),
    DANGER(R.color.tone_danger, R.color.tone_danger_bg),
    INFO(R.color.tone_info, R.color.tone_info_bg),
    GOLD(R.color.tone_gold, R.color.tone_gold_bg),
    AI(R.color.tone_ai, R.color.tone_ai_bg);

    @ColorRes public final int fg;
    @ColorRes public final int bg;

    Tone(@ColorRes int fg, @ColorRes int bg) {
        this.fg = fg;
        this.bg = bg;
    }

    public void badge(TextView v) {
        v.setTextColor(ContextCompat.getColor(v.getContext(), fg));
        v.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(v.getContext(), bg)));
    }

    public void tile(View tile, @Nullable ImageView icon) {
        tile.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(tile.getContext(), bg)));
        if (icon != null) ImageViewCompat.setImageTintList(icon, ColorStateList.valueOf(ContextCompat.getColor(icon.getContext(), fg)));
    }

    public void strip(View strip) {
        strip.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(strip.getContext(), fg)));
    }

    /** Maps common backend status codes to a tone. */
    public static Tone forStatus(@Nullable String status) {
        if (status == null) return NEUTRAL;
        switch (status) {
            case "ACTIVE": case "PAID": case "DONE": case "SETTLED": case "RECONCILED": case "COMPLETED":
            case "OPEN_RESOLVED": case "RESOLVED": case "VERIFIED":
                return SUCCESS;
            case "PENDING": case "PENDING_VERIFICATION": case "PROCESSING": case "QUEUED": case "RUNNING":
            case "POSTPONED": case "RESERVED": case "WAITING": case "IN_PROGRESS":
                return WARNING;
            case "FAILED": case "CANCELLED": case "EXPIRED": case "REJECTED": case "HIGH": case "URGENT": case "OVERDUE":
                return DANGER;
            case "NEW": case "OPEN": case "SCHEDULED": case "UPCOMING": case "MEDIUM":
                return INFO;
            case "CLOSED": case "ARCHIVED": case "LOW": case "INFO":
                return NEUTRAL;
            default:
                return NEUTRAL;
        }
    }
}
