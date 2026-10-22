package com.legalguide.app.ui.cases;

import androidx.annotation.Nullable;

import com.legalguide.app.R;
import com.legalguide.app.core.net.ApiClient;
import com.legalguide.app.core.session.Session;
import com.legalguide.app.data.model.Hearing;
import com.legalguide.app.data.model.Page;
import com.legalguide.app.ui.common.ListController;
import com.legalguide.app.ui.common.RowAdapter;
import com.legalguide.app.ui.hearings.HearingFormActivity;
import com.legalguide.app.ui.hearings.HearingsActivity;

import retrofit2.Call;

public class CaseHearingsFragment extends CaseTabListFragment<Hearing> {

    @Override
    protected Call<Page<Hearing>> load(String caseId, @Nullable String cursor) {
        return ApiClient.api().caseHearings(caseId, cursor);
    }

    @Override
    protected void bind(RowAdapter.Row row, Hearing h) {
        HearingsActivity.bindHearing(row, h, false);
    }

    @Override
    protected void onItemClick(Hearing h) {
        HearingFormActivity.edit(requireContext(), h.id);
    }

    @Override
    protected void configure(ListController<Hearing> c) {
        c.empty(R.drawable.ic_calendar, getString(R.string.hearings_empty_title), getString(R.string.case_hearings_empty));
    }

    @Override public int fabText() { return R.string.hearing_new; }
    @Override public int fabIcon() { return R.drawable.ic_add; }
    @Override public void onFab() { HearingFormActivity.create(requireContext(), vm.caseId()); }
    @Override public boolean fabVisible() { return Session.get().can("hearings.create"); }
}
