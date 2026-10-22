package com.legalguide.app.ui.support;

import android.content.Intent;
import android.view.View;

import androidx.annotation.Nullable;

import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.legalguide.app.R;
import com.legalguide.app.core.net.ApiClient;
import com.legalguide.app.core.util.Dates;
import com.legalguide.app.core.util.Texts;
import com.legalguide.app.data.model.Page;
import com.legalguide.app.data.model.Ticket;
import com.legalguide.app.ui.common.ListController;
import com.legalguide.app.ui.common.RowAdapter;
import com.legalguide.app.ui.common.SimpleListActivity;
import com.legalguide.app.ui.common.Tone;

import retrofit2.Call;

public class TicketsActivity extends SimpleListActivity<Ticket> {

    @Override
    protected CharSequence title() {
        return getString(R.string.tickets_title);
    }

    @Override
    protected Call<Page<Ticket>> load(@Nullable String cursor) {
        return ApiClient.api().tickets(cursor);
    }

    @Override
    protected void bind(RowAdapter.Row row, Ticket t) {
        row.icon(R.drawable.ic_headset, Tone.INFO).title(t.subject).subtitle(t.lastMessage)
                .meta(Texts.joinNonBlank(" • ", "#" + Texts.orDash(t.id), Dates.relative(t.updatedAt != null ? t.updatedAt : t.createdAt)))
                .badge(t.statusLabel, Tone.forStatus(t.status));
    }

    @Override
    protected void onItemClick(Ticket t) {
        TicketDetailActivity.start(this, t.id);
    }

    @Override
    protected void configure(ListController<Ticket> c) {
        c.empty(R.drawable.ic_headset, getString(R.string.tickets_empty_title), getString(R.string.tickets_empty_message));
    }

    @Override
    protected void setupFab(ExtendedFloatingActionButton fab) {
        fab.setVisibility(View.VISIBLE);
        fab.setText(R.string.ticket_new);
        fab.setIconResource(R.drawable.ic_add);
        fab.setOnClickListener(v -> startActivity(new Intent(this, NewTicketActivity.class)));
    }
}
