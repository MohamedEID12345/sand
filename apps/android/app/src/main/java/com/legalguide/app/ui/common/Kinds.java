package com.legalguide.app.ui.common;

import android.content.Context;

import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;

import com.legalguide.app.R;

/** Icon / tone / label for the item types returned by library and global search. */
public final class Kinds {
    private Kinds() {}

    @DrawableRes
    public static int icon(@Nullable String type) {
        if (type == null) return R.drawable.ic_document;
        switch (type) {
            case "LAW": return R.drawable.ic_library;
            case "BOOK": return R.drawable.ic_library;
            case "ARTICLE": return R.drawable.ic_document;
            case "JUDGMENT": return R.drawable.ic_gavel;
            case "PRINCIPLE": return R.drawable.ic_verified;
            case "UPDATE": return R.drawable.ic_campaign;
            case "CASE": return R.drawable.ic_briefcase;
            case "CLIENT": return R.drawable.ic_person;
            case "CONTRACT": return R.drawable.ic_contract;
            case "NOTE": return R.drawable.ic_notes;
            case "HEARING": return R.drawable.ic_calendar;
            case "CHAT": return R.drawable.ic_chat;
            default: return R.drawable.ic_document;
        }
    }

    public static Tone tone(@Nullable String type) {
        if (type == null) return Tone.NEUTRAL;
        switch (type) {
            case "LAW": case "ARTICLE": return Tone.PRIMARY;
            case "JUDGMENT": case "PRINCIPLE": return Tone.GOLD;
            case "BOOK": return Tone.INFO;
            case "UPDATE": return Tone.WARNING;
            case "CASE": case "HEARING": return Tone.PRIMARY;
            case "CLIENT": return Tone.GOLD;
            case "CONTRACT": return Tone.SUCCESS;
            case "CHAT": return Tone.AI;
            default: return Tone.NEUTRAL;
        }
    }

    public static String label(Context c, @Nullable String type) {
        if (type == null) return "";
        switch (type) {
            case "LAW": return c.getString(R.string.kind_law);
            case "ARTICLE": return c.getString(R.string.kind_article);
            case "JUDGMENT": return c.getString(R.string.kind_judgment);
            case "PRINCIPLE": return c.getString(R.string.kind_principle);
            case "BOOK": return c.getString(R.string.kind_book);
            case "UPDATE": return c.getString(R.string.kind_update);
            case "CASE": return c.getString(R.string.kind_case);
            case "CLIENT": return c.getString(R.string.kind_client);
            case "CONTRACT": return c.getString(R.string.kind_contract);
            case "NOTE": return c.getString(R.string.kind_note);
            case "HEARING": return c.getString(R.string.kind_hearing);
            case "DOCUMENT": return c.getString(R.string.kind_document);
            default: return "";
        }
    }

    /** Legal status of a source: IN_FORCE / AMENDED / REPEALED. */
    @Nullable
    public static String statusLabel(Context c, @Nullable String status) {
        if (status == null) return null;
        switch (status) {
            case "IN_FORCE": return c.getString(R.string.status_in_force);
            case "AMENDED": return c.getString(R.string.status_amended);
            case "REPEALED": return c.getString(R.string.status_repealed);
            default: return null;
        }
    }

    public static Tone statusTone(@Nullable String status) {
        if ("REPEALED".equals(status)) return Tone.DANGER;
        if ("AMENDED".equals(status)) return Tone.WARNING;
        return Tone.SUCCESS;
    }
}
