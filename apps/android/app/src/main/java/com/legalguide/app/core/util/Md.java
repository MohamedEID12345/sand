package com.legalguide.app.core.util;

import android.content.Context;
import android.widget.TextView;

import androidx.annotation.Nullable;

import io.noties.markwon.Markwon;
import io.noties.markwon.ext.tables.TablePlugin;

/** Markdown rendering for AI answers, contracts and legal texts. */
public final class Md {
    private static Markwon markwon;

    private Md() {}

    public static synchronized Markwon get(Context ctx) {
        if (markwon == null) {
            markwon = Markwon.builder(ctx.getApplicationContext())
                    .usePlugin(TablePlugin.create(ctx.getApplicationContext()))
                    .build();
        }
        return markwon;
    }

    public static void render(TextView tv, @Nullable String markdown) {
        get(tv.getContext()).setMarkdown(tv, markdown == null ? "" : markdown);
    }
}
