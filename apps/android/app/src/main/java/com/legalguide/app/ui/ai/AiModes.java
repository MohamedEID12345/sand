package com.legalguide.app.ui.ai;

import android.content.Context;

import androidx.annotation.ArrayRes;
import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.legalguide.app.R;
import com.legalguide.app.core.session.Session;
import com.legalguide.app.data.model.Wallet;
import com.legalguide.app.ui.common.Tone;

/** The AI assistant's working modes. Costs come from the server (GET /ai/costs, operation = mode code). */
public enum AiModes {
    QUESTION("QUESTION", R.drawable.ic_ai, Tone.AI, R.string.mode_question, R.string.mode_question_desc, R.array.suggest_question),
    RESEARCH("RESEARCH", R.drawable.ic_search, Tone.INFO, R.string.mode_research, R.string.mode_research_desc, R.array.suggest_research),
    DEEP_RESEARCH("DEEP_RESEARCH", R.drawable.ic_insights, Tone.INFO, R.string.mode_deep_research, R.string.mode_deep_research_desc, R.array.suggest_research),
    MEMO("MEMO", R.drawable.ic_notes, Tone.GOLD, R.string.mode_memo, R.string.mode_memo_desc, R.array.suggest_memo),
    DEBATE("DEBATE", R.drawable.ic_debate, Tone.DANGER, R.string.mode_debate, R.string.mode_debate_desc, R.array.suggest_debate),
    COMPARE("COMPARE", R.drawable.ic_copy, Tone.SUCCESS, R.string.mode_compare, R.string.mode_compare_desc, R.array.suggest_compare),
    DOCUMENT("DOCUMENT", R.drawable.ic_document, Tone.PRIMARY, R.string.mode_document, R.string.mode_document_desc, R.array.suggest_document);

    public final String code;
    @DrawableRes public final int icon;
    public final Tone tone;
    @StringRes public final int title;
    @StringRes public final int desc;
    @ArrayRes public final int suggestions;

    AiModes(String code, int icon, Tone tone, int title, int desc, int suggestions) {
        this.code = code;
        this.icon = icon;
        this.tone = tone;
        this.title = title;
        this.desc = desc;
        this.suggestions = suggestions;
    }

    public static AiModes of(@Nullable String code) {
        if (code != null) for (AiModes m : values()) if (m.code.equals(code)) return m;
        return QUESTION;
    }

    /** Admin feature flag per mode, e.g. "ai.deep_research". */
    public boolean enabled() {
        return Session.get().feature("ai") && Session.get().feature("ai." + code.toLowerCase());
    }

    /** "1 رصيد" / "استخدام مجاني" / "" — informational only; the server decides the actual charge. */
    public String costLabel(Context c) {
        Wallet w = Session.get().wallet();
        if (w != null && w.freeServiceUses > 0) return c.getString(R.string.cost_free_use);
        int cost = Session.get().costOf(code);
        return cost > 0 ? c.getString(R.string.credits_count, String.valueOf(cost)) : "";
    }
}
