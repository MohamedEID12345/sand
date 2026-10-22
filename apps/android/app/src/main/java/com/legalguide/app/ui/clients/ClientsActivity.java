package com.legalguide.app.ui.clients;

import android.content.Intent;

import androidx.annotation.Nullable;

import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.legalguide.app.R;
import com.legalguide.app.core.net.ApiClient;
import com.legalguide.app.core.session.Session;
import com.legalguide.app.core.util.Texts;
import com.legalguide.app.data.model.Client;
import com.legalguide.app.data.model.Page;
import com.legalguide.app.ui.common.ListController;
import com.legalguide.app.ui.common.RowAdapter;
import com.legalguide.app.ui.common.SimpleListActivity;
import com.legalguide.app.ui.common.Tone;

import retrofit2.Call;

public class ClientsActivity extends SimpleListActivity<Client> {

    @Override
    protected CharSequence title() {
        return getString(R.string.clients_title);
    }

    @Override
    protected Call<Page<Client>> load(@Nullable String cursor) {
        return ApiClient.api().clients(query(), cursor);
    }

    @Override
    protected void bind(RowAdapter.Row row, Client c) {
        row.avatar(c.name).title(c.name)
                .subtitle(Texts.joinNonBlank(" • ", c.phone, c.email))
                .meta(c.casesCount > 0 ? getString(R.string.client_cases_count, c.casesCount) : null)
                .badge(c.casesCount > 0 ? null : getString(R.string.client_no_cases), Tone.NEUTRAL);
    }

    @Override
    protected void onItemClick(Client c) {
        ClientDetailActivity.start(this, c.id);
    }

    @Override
    protected boolean hasSearch() {
        return true;
    }

    @Override
    protected CharSequence searchHint() {
        return getString(R.string.clients_search_hint);
    }

    @Override
    protected void configure(ListController<Client> c) {
        c.empty(R.drawable.ic_people, getString(R.string.clients_empty_title), getString(R.string.clients_empty_message));
    }

    @Override
    protected void setupFab(ExtendedFloatingActionButton fab) {
        if (!Session.get().can("clients.create")) return;
        fab.setVisibility(android.view.View.VISIBLE);
        fab.setText(R.string.client_new);
        fab.setIconResource(R.drawable.ic_add);
        fab.setOnClickListener(v -> startActivity(new Intent(this, ClientFormActivity.class)));
    }
}
