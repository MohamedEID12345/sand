package com.legalguide.app.ui.cases;

import androidx.annotation.Nullable;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.legalguide.app.R;
import com.legalguide.app.core.net.ApiClient;
import com.legalguide.app.core.session.Session;
import com.legalguide.app.core.util.Dates;
import com.legalguide.app.core.util.Json;
import com.legalguide.app.core.util.Texts;
import com.legalguide.app.data.model.Analysis;
import com.legalguide.app.data.model.AnalysisType;
import com.legalguide.app.data.model.CaseMeta;
import com.legalguide.app.data.model.Page;
import com.legalguide.app.ui.common.Dialogs;
import com.legalguide.app.ui.common.ListController;
import com.legalguide.app.ui.common.RowAdapter;
import com.legalguide.app.ui.common.Tone;

import java.util.List;

import retrofit2.Call;

/** AI case analyses: weaknesses, evidence matrix, contradictions, timeline... (no outcome prediction). */
public class CaseAnalysesFragment extends CaseTabListFragment<Analysis> {

    @Override
    protected Call<Page<Analysis>> load(String caseId, @Nullable String cursor) {
        return ApiClient.api().caseAnalyses(caseId, cursor);
    }

    @Override
    protected void bind(RowAdapter.Row row, Analysis a) {
        row.icon(R.drawable.ic_insights, Tone.AI)
                .title(Texts.orDash(a.typeLabel))
                .subtitle(a.summary)
                .meta(Texts.joinNonBlank(" • ", Dates.relative(a.createdAt),
                        a.creditsCharged > 0 ? getString(R.string.cost_charged, a.creditsCharged) : null))
                .badge(statusLabel(a.status), Tone.forStatus(a.status));
    }

    private String statusLabel(@Nullable String s) {
        if ("DONE".equals(s)) return getString(R.string.analysis_done);
        if ("FAILED".equals(s)) return getString(R.string.analysis_failed);
        return getString(R.string.analysis_running);
    }

    @Override
    protected void onItemClick(Analysis a) {
        AnalysisActivity.start(requireContext(), a.id);
    }

    @Override
    protected void configure(ListController<Analysis> c) {
        c.empty(R.drawable.ic_insights, getString(R.string.analysis_empty_title), getString(R.string.analysis_empty_message));
    }

    @Override public int fabText() { return R.string.analysis_new; }
    @Override public int fabIcon() { return R.drawable.ic_ai; }
    @Override public boolean fabVisible() { return Session.get().feature("ai") && Session.get().feature("case_analysis"); }

    @Override
    public void onFab() {
        CaseMeta meta = Session.get().caseMeta();
        List<AnalysisType> types = meta != null ? meta.analysisTypes : null;
        if (types == null || types.isEmpty()) {
            Dialogs.info(requireContext(), getString(R.string.analysis_new), getString(R.string.analysis_unavailable));
            return;
        }
        String[] labels = new String[types.size()];
        for (int i = 0; i < types.size(); i++) {
            AnalysisType t = types.get(i);
            labels[i] = t.credits > 0 ? t.label + " — " + getString(R.string.credits_count, String.valueOf(t.credits)) : t.label;
        }
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.analysis_pick_type)
                .setItems(labels, (d, which) -> {
                    AnalysisType t = types.get(which);
                    Dialogs.confirmCost(requireContext(), t.label, t.credits, () -> run(t));
                })
                .show();
    }

    private void run(AnalysisType t) {
        call(ApiClient.api().runAnalysis(vm.caseId(), Json.map("type", t.code)), res -> {
            if (res == null) return;
            Session.get().updateWallet(res.wallet);
            controller.refresh();
            if (res.analysis != null) AnalysisActivity.start(requireContext(), res.analysis.id);
        });
    }
}
