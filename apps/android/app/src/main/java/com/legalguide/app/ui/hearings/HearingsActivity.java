package com.legalguide.app.ui.hearings;

import android.content.Context;

import androidx.annotation.Nullable;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.legalguide.app.R;
import com.legalguide.app.core.net.ApiClient;
import com.legalguide.app.core.notify.ReminderScheduler;
import com.legalguide.app.core.util.Dates;
import com.legalguide.app.core.util.Texts;
import com.legalguide.app.data.model.Hearing;
import com.legalguide.app.data.model.Page;
import com.legalguide.app.ui.common.ListController;
import com.legalguide.app.ui.common.RowAdapter;
import com.legalguide.app.ui.common.SimpleListActivity;
import com.legalguide.app.ui.common.Tone;
import com.legalguide.app.ui.common.Ui;

import retrofit2.Call;

/** Court agenda across all cases, grouped by day. */
public class HearingsActivity extends SimpleListActivity<Hearing> {
    private String scope = "upcoming";

    @Override
    protected CharSequence title() {
        return getString(R.string.hearings_title);
    }

    @Override
    protected Call<Page<Hearing>> load(@Nullable String cursor) {
        return ApiClient.api().hearings(scope, null, cursor);
    }

    @Override
    protected void configureAdapter(RowAdapter<Hearing> a) {
        a.groupBy(h -> Dates.groupKey(h.hearingDate));
    }

    @Override
    protected void configure(ListController<Hearing> c) {
        c.empty(R.drawable.ic_calendar, getString(R.string.hearings_empty_title), getString(R.string.hearings_empty_message));
        c.onLoaded((items, first) -> {
            if ("upcoming".equals(scope)) ReminderScheduler.scheduleAll(this, items);
        });
    }

    @Override
    protected void bind(RowAdapter.Row row, Hearing h) {
        bindHearing(row, h, true);
    }

    public static void bindHearing(RowAdapter.Row row, Hearing h, boolean showCase) {
        Context c = row.b.getRoot().getContext();
        long d = Dates.daysUntil(h.hearingDate);
        Tone tone = h.status != null && !"SCHEDULED".equals(h.status) ? Tone.forStatus(h.status)
                : d < 0 ? Tone.NEUTRAL : d <= 1 ? Tone.DANGER : d <= 3 ? Tone.WARNING : Tone.INFO;
        row.icon(R.drawable.ic_calendar, tone)
                .title(showCase ? Texts.orDash(Texts.blank(h.caseTitle) ? h.caseNumber : h.caseTitle) : Dates.dayTime(h.hearingDate))
                .subtitle(Texts.joinNonBlank(" • ", h.court, h.circuit, h.purpose))
                .meta(showCase ? Texts.joinNonBlank(" • ", Dates.time(h.hearingDate), h.decision) : h.decision)
                .badge(h.statusLabel != null ? h.statusLabel
                        : d == 0 ? c.getString(R.string.today) : d == 1 ? c.getString(R.string.tomorrow) : null, tone);
    }

    @Override
    protected void onItemClick(Hearing h) {
        HearingFormActivity.edit(this, h.id);
    }

    @Override
    protected boolean setupFilters(ChipGroup group) {
        String[] scopes = {"upcoming", "today", "past"};
        int[] labels = {R.string.hearings_upcoming, R.string.today, R.string.hearings_past};
        for (int i = 0; i < scopes.length; i++) {
            String s = scopes[i];
            Chip chip = Ui.chip(group, getString(labels[i]), true);
            chip.setChecked(s.equals(scope));
            chip.setOnClickListener(v -> {
                scope = s;
                reload();
            });
        }
        return true;
    }
}
