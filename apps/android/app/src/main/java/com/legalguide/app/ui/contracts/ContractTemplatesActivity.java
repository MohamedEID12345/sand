package com.legalguide.app.ui.contracts;

import android.content.Intent;

import androidx.annotation.Nullable;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.legalguide.app.R;
import com.legalguide.app.core.net.ApiClient;
import com.legalguide.app.data.model.ContractTemplate;
import com.legalguide.app.data.model.Option;
import com.legalguide.app.data.model.Page;
import com.legalguide.app.ui.common.ListController;
import com.legalguide.app.ui.common.RowAdapter;
import com.legalguide.app.ui.common.SimpleListActivity;
import com.legalguide.app.ui.common.Tone;
import com.legalguide.app.ui.common.Ui;

import retrofit2.Call;

/** Contract template catalog (admin-managed). */
public class ContractTemplatesActivity extends SimpleListActivity<ContractTemplate> {
    @Nullable private String category;

    @Override
    protected CharSequence title() {
        return getString(R.string.contracts_templates_title);
    }

    @Override
    protected Call<Page<ContractTemplate>> load(@Nullable String cursor) {
        return ApiClient.api().contractTemplates(query(), category, cursor);
    }

    @Override
    protected void bind(RowAdapter.Row row, ContractTemplate t) {
        row.icon(R.drawable.ic_contract, Tone.SUCCESS).title(t.name).subtitle(t.description)
                .meta(t.categoryLabel)
                .badge(t.credits > 0 ? getString(R.string.credits_count, String.valueOf(t.credits)) : null, Tone.GOLD);
    }

    @Override
    protected void onItemClick(ContractTemplate t) {
        ContractWizardActivity.start(this, t.id);
    }

    @Override
    protected boolean hasSearch() {
        return true;
    }

    @Override
    protected CharSequence searchHint() {
        return getString(R.string.contracts_search_hint);
    }

    @Override
    protected void configure(ListController<ContractTemplate> c) {
        c.empty(R.drawable.ic_contract, getString(R.string.contracts_templates_empty), null);
    }

    @Override
    protected boolean setupFilters(ChipGroup group) {
        call(ApiClient.api().contractCategories(), page -> {
            if (page == null || page.items == null || page.items.isEmpty()) return;
            Chip all = Ui.chip(group, getString(R.string.filter_all), true);
            all.setChecked(true);
            all.setOnClickListener(v -> select(null));
            for (Option o : page.items) {
                Chip c = Ui.chip(group, o.label, true);
                c.setOnClickListener(v -> select(o.value));
            }
            b.filtersScroll.setVisibility(android.view.View.VISIBLE);
        }, e -> { });
        return false;
    }

    private void select(@Nullable String c) {
        category = c;
        reload();
    }

    @Override
    protected void onReady() {
        b.toolbar.inflateMenu(R.menu.menu_contracts);
        b.toolbar.setOnMenuItemClickListener(item -> {
            startActivity(new Intent(this, MyContractsActivity.class));
            return true;
        });
    }

    @Override
    protected boolean reloadOnReturn() {
        return false;
    }
}
