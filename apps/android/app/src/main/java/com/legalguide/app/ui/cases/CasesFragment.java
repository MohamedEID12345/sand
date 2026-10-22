package com.legalguide.app.ui.cases;

import android.content.Intent;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.chip.Chip;
import com.legalguide.app.R;
import com.legalguide.app.core.net.ApiClient;
import com.legalguide.app.core.session.Session;
import com.legalguide.app.core.util.Dates;
import com.legalguide.app.core.util.Texts;
import com.legalguide.app.data.model.CaseMeta;
import com.legalguide.app.data.model.LegalCase;
import com.legalguide.app.data.model.Option;
import com.legalguide.app.data.model.Page;
import com.legalguide.app.ui.clients.ClientsActivity;
import com.legalguide.app.ui.common.ListPageFragment;
import com.legalguide.app.ui.common.PagedAdapter;
import com.legalguide.app.ui.common.RowAdapter;
import com.legalguide.app.ui.common.Tone;
import com.legalguide.app.ui.common.Ui;
import com.legalguide.app.ui.hearings.HearingsActivity;

import retrofit2.Call;

public class CasesFragment extends ListPageFragment<LegalCase> {
    private RowAdapter<LegalCase> adapter;
    @Nullable private String status;

    @Override
    protected RecyclerView.Adapter<?> createAdapter() {
        adapter = new RowAdapter<LegalCase>((row, c) -> bindCase(row, c))
                .onClick(c -> CaseDetailActivity.start(requireContext(), c.id));
        return adapter;
    }

    /** Shared by the client screen. */
    public static void bindCase(RowAdapter.Row row, LegalCase c) {
        android.content.Context ctx = row.b.getRoot().getContext();
        Tone pr = "HIGH".equals(c.priority) || "URGENT".equals(c.priority) ? Tone.DANGER : Tone.PRIMARY;
        row.icon(R.drawable.ic_briefcase, pr)
                .title(Texts.joinNonBlank(" — ", c.displayTitle(), c.caseTypeName))
                .subtitle(Texts.joinNonBlank(" • ",
                        c.clientName == null ? null : ctx.getString(R.string.case_client_short, c.clientName),
                        c.opponent == null ? null : ctx.getString(R.string.case_opponent_short, c.opponent)))
                .meta(Texts.joinNonBlank(" • ", c.court,
                        c.nextHearingAt == null ? null : ctx.getString(R.string.case_next_hearing_short, Dates.dayShort(c.nextHearingAt))))
                .badge(c.statusLabel, Tone.forStatus(c.status));
    }

    @Override
    protected PagedAdapter<LegalCase> pagedAdapter() {
        return adapter;
    }

    @Override
    protected Call<Page<LegalCase>> load(@Nullable String cursor) {
        return ApiClient.api().cases(query(), status, null, cursor);
    }

    @Override
    protected boolean hasSearch() {
        return true;
    }

    @Override
    protected CharSequence searchHint() {
        return getString(R.string.cases_search_hint);
    }

    @Override
    protected void setupPage() {
        b.pageTitle.setText(R.string.nav_cases);
        b.pageAction1.setVisibility(View.VISIBLE);
        b.pageAction1.setIconResource(R.drawable.ic_calendar);
        b.pageAction1.setContentDescription(getString(R.string.hearings_title));
        b.pageAction1.setOnClickListener(v -> startActivity(new Intent(requireContext(), HearingsActivity.class)));
        b.pageAction2.setVisibility(View.VISIBLE);
        b.pageAction2.setIconResource(R.drawable.ic_people);
        b.pageAction2.setContentDescription(getString(R.string.clients_title));
        b.pageAction2.setOnClickListener(v -> startActivity(new Intent(requireContext(), ClientsActivity.class)));

        if (Session.get().can("cases.create")) {
            b.fab.setVisibility(View.VISIBLE);
            b.fab.setText(R.string.case_new);
            b.fab.setIconResource(R.drawable.ic_add);
            b.fab.setOnClickListener(v -> startActivity(new Intent(requireContext(), CaseFormActivity.class)));
            controller.emptyAction(getString(R.string.case_new), v -> startActivity(new Intent(requireContext(), CaseFormActivity.class)));
        }
        controller.empty(R.drawable.ic_briefcase, getString(R.string.cases_empty_title), getString(R.string.cases_empty_message));

        CaseMeta meta = Session.get().caseMeta();
        if (meta != null) buildFilters(meta);
        else call(ApiClient.api().caseMeta(), m -> {
            Session.get().setCaseMeta(m);
            if (m != null && b != null) buildFilters(m);
        }, e -> { });
    }

    private void buildFilters(CaseMeta meta) {
        if (meta.statuses == null || meta.statuses.isEmpty()) return;
        b.filters.removeAllViews();
        b.filtersScroll.setVisibility(View.VISIBLE);
        Chip all = Ui.chip(b.filters, getString(R.string.filter_all), true);
        all.setChecked(status == null);
        all.setOnClickListener(v -> select(null));
        for (Option o : meta.statuses) {
            Chip c = Ui.chip(b.filters, o.label, true);
            c.setChecked(o.value.equals(status));
            c.setOnClickListener(v -> select(o.value));
        }
    }

    private void select(@Nullable String s) {
        status = s;
        controller.refresh();
    }
}
