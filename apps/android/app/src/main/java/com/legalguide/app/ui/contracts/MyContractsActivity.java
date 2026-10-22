package com.legalguide.app.ui.contracts;

import android.content.Intent;

import androidx.annotation.Nullable;

import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.legalguide.app.R;
import com.legalguide.app.core.net.ApiClient;
import com.legalguide.app.core.util.Dates;
import com.legalguide.app.core.util.Texts;
import com.legalguide.app.data.model.Contract;
import com.legalguide.app.data.model.Page;
import com.legalguide.app.ui.common.ListController;
import com.legalguide.app.ui.common.RowAdapter;
import com.legalguide.app.ui.common.SimpleListActivity;
import com.legalguide.app.ui.common.Tone;

import retrofit2.Call;

public class MyContractsActivity extends SimpleListActivity<Contract> {

    @Override
    protected CharSequence title() {
        return getString(R.string.contracts_mine_title);
    }

    @Override
    protected Call<Page<Contract>> load(@Nullable String cursor) {
        return ApiClient.api().contracts(cursor);
    }

    @Override
    protected void bind(RowAdapter.Row row, Contract c) {
        boolean notes = c.reviewNotes != null && !c.reviewNotes.isEmpty();
        row.icon(R.drawable.ic_document, Tone.SUCCESS)
                .title(Texts.blank(c.title) ? c.templateName : c.title)
                .subtitle(c.caseTitle)
                .meta(Texts.joinNonBlank(" • ", c.templateName, Dates.day(c.createdAt)))
                .badge(notes ? getString(R.string.contract_has_notes, c.reviewNotes.size()) : null, Tone.WARNING);
    }

    @Override
    protected void onItemClick(Contract c) {
        ContractViewActivity.start(this, c.id);
    }

    @Override
    protected void configure(ListController<Contract> c) {
        c.empty(R.drawable.ic_contract, getString(R.string.contracts_mine_empty), getString(R.string.contracts_mine_empty_message));
    }

    @Override
    protected void setupFab(ExtendedFloatingActionButton fab) {
        fab.setVisibility(android.view.View.VISIBLE);
        fab.setText(R.string.home_new_contract);
        fab.setIconResource(R.drawable.ic_add);
        fab.setOnClickListener(v -> startActivity(new Intent(this, ContractTemplatesActivity.class)));
    }
}
