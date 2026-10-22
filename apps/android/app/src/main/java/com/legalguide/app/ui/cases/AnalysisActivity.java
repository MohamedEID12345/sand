package com.legalguide.app.ui.cases;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;

import androidx.annotation.Nullable;

import com.google.android.material.chip.Chip;
import com.legalguide.app.R;
import com.legalguide.app.core.net.ApiClient;
import com.legalguide.app.core.util.Dates;
import com.legalguide.app.core.util.Intents;
import com.legalguide.app.core.util.Md;
import com.legalguide.app.core.util.Texts;
import com.legalguide.app.data.model.Analysis;
import com.legalguide.app.data.model.AnalysisItem;
import com.legalguide.app.data.model.AnalysisSection;
import com.legalguide.app.data.model.Citation;
import com.legalguide.app.databinding.ActivityAnalysisBinding;
import com.legalguide.app.databinding.ItemAnalysisFindingBinding;
import com.legalguide.app.databinding.ItemSectionHeaderBinding;
import com.legalguide.app.ui.common.BaseActivity;
import com.legalguide.app.ui.common.Router;
import com.legalguide.app.ui.common.Tone;
import com.legalguide.app.ui.common.Ui;

/** Displays an AI case analysis; polls while the server is still working on it. */
public class AnalysisActivity extends BaseActivity {
    private static final String EXTRA_ID = "analysis_id";
    private static final long POLL_MS = 4000, POLL_MAX_MS = 6 * 60 * 1000;

    private ActivityAnalysisBinding b;
    private String id;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private long pollStarted;
    @Nullable private Analysis current;

    public static void start(Context c, String analysisId) {
        c.startActivity(new Intent(c, AnalysisActivity.class).putExtra(EXTRA_ID, analysisId));
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!ensureSession()) return;
        b = ActivityAnalysisBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        applyInsets(b.getRoot());
        setupToolbar(b.appBar.toolbar, R.string.analysis_title, true);
        id = getIntent().getStringExtra(EXTRA_ID);
        b.appBar.toolbar.inflateMenu(R.menu.menu_share);
        b.appBar.toolbar.setOnMenuItemClickListener(item -> {
            if (current != null) Intents.shareText(this, current.typeLabel, asText(current));
            return true;
        });
        b.state.loading();
        pollStarted = System.currentTimeMillis();
        load();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    private void load() {
        call(ApiClient.api().analysis(id), a -> {
            if (a == null) return;
            current = a;
            b.state.hide();
            render(a);
            if (a.isRunning() && System.currentTimeMillis() - pollStarted < POLL_MAX_MS) handler.postDelayed(this::load, POLL_MS);
        }, e -> {
            if (current == null) b.state.error(e, this::load);
            else showError(e);
        });
    }

    private void render(Analysis a) {
        b.appBar.toolbar.setTitle(Texts.blank(a.typeLabel) ? getString(R.string.analysis_title) : a.typeLabel);
        b.meta.setText(Texts.joinNonBlank(" • ", Dates.dayTime(a.createdAt),
                a.creditsCharged > 0 ? getString(R.string.cost_charged, a.creditsCharged) : null));
        b.running.setVisibility(a.isRunning() ? View.VISIBLE : View.GONE);
        boolean failed = "FAILED".equals(a.status);
        b.failed.setVisibility(failed ? View.VISIBLE : View.GONE);
        if (failed) b.failedMessage.setText(Texts.blank(a.error) ? getString(R.string.analysis_failed_message) : a.error);

        Ui.show(b.summaryCard, !Texts.blank(a.summary));
        Md.render(b.summary, a.summary);

        b.sections.removeAllViews();
        if (a.sections != null) {
            LayoutInflater inf = getLayoutInflater();
            for (AnalysisSection s : a.sections) {
                ItemSectionHeaderBinding h = ItemSectionHeaderBinding.inflate(inf, b.sections, true);
                h.sectionTitle.setText(s.title);
                h.sectionTitle.setPadding(0, Ui.dp(this, 16), 0, Ui.dp(this, 6));
                if (s.items == null) continue;
                for (AnalysisItem it : s.items) {
                    ItemAnalysisFindingBinding f = ItemAnalysisFindingBinding.inflate(inf, b.sections, true);
                    Tone tone = severityTone(it.severity);
                    tone.strip(f.findingStrip);
                    f.findingTitle.setText(it.title);
                    Ui.text(f.findingSeverity, severityLabel(it.severity));
                    if (f.findingSeverity.getVisibility() == View.VISIBLE) tone.badge(f.findingSeverity);
                    Md.render(f.findingBody, it.body);
                    Ui.show(f.findingBody, !Texts.blank(it.body));
                    f.findingFields.removeAllViews();
                    Ui.kvList(f.findingFields, it.fields);
                    Ui.text(f.findingRefs, it.refs == null || it.refs.isEmpty() ? null
                            : getString(R.string.analysis_refs, Texts.join(it.refs, "، ")));
                }
            }
        }

        b.citations.removeAllViews();
        boolean hasCit = a.citations != null && !a.citations.isEmpty();
        Ui.show(b.citationsCard, hasCit);
        if (hasCit) {
            for (Citation c : a.citations) {
                Chip chip = Ui.chip(b.citations, com.legalguide.app.ui.ai.MessageAdapterLabels.label(c), false);
                chip.setChipIconResource(c.verified ? R.drawable.ic_verified : R.drawable.ic_warning);
                chip.setChipIconVisible(true);
                chip.setOnClickListener(v -> Router.openCitation(this, c));
            }
        }
        b.disclaimer.setText(Texts.blank(a.disclaimer) ? getString(R.string.analysis_disclaimer) : a.disclaimer);
    }

    private static Tone severityTone(@Nullable String s) {
        if ("HIGH".equals(s)) return Tone.DANGER;
        if ("MEDIUM".equals(s)) return Tone.WARNING;
        if ("LOW".equals(s)) return Tone.INFO;
        return Tone.NEUTRAL;
    }

    @Nullable
    private String severityLabel(@Nullable String s) {
        if ("HIGH".equals(s)) return getString(R.string.severity_high);
        if ("MEDIUM".equals(s)) return getString(R.string.severity_medium);
        if ("LOW".equals(s)) return getString(R.string.severity_low);
        return null;
    }

    private String asText(Analysis a) {
        StringBuilder sb = new StringBuilder();
        sb.append(Texts.orDash(a.typeLabel)).append("\n\n");
        if (!Texts.blank(a.summary)) sb.append(a.summary).append("\n\n");
        if (a.sections != null) for (AnalysisSection s : a.sections) {
            sb.append("## ").append(s.title).append('\n');
            if (s.items != null) for (AnalysisItem it : s.items) {
                sb.append("- ").append(it.title);
                if (!Texts.blank(it.body)) sb.append(": ").append(it.body);
                sb.append('\n');
            }
            sb.append('\n');
        }
        sb.append(Texts.blank(a.disclaimer) ? getString(R.string.analysis_disclaimer) : a.disclaimer);
        return sb.toString();
    }
}
