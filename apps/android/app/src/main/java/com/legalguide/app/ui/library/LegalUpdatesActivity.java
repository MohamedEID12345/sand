package com.legalguide.app.ui.library;

import androidx.annotation.Nullable;

import com.legalguide.app.R;
import com.legalguide.app.core.net.ApiClient;
import com.legalguide.app.core.util.Dates;
import com.legalguide.app.data.model.LegalUpdate;
import com.legalguide.app.data.model.Page;
import com.legalguide.app.ui.common.ListController;
import com.legalguide.app.ui.common.RowAdapter;
import com.legalguide.app.ui.common.Router;
import com.legalguide.app.ui.common.SimpleListActivity;
import com.legalguide.app.ui.common.Tone;

import retrofit2.Call;

public class LegalUpdatesActivity extends SimpleListActivity<LegalUpdate> {

    @Override
    protected CharSequence title() {
        return getString(R.string.updates_title);
    }

    @Override
    protected Call<Page<LegalUpdate>> load(@Nullable String cursor) {
        return ApiClient.api().legalUpdates(cursor);
    }

    @Override
    protected void bind(RowAdapter.Row row, LegalUpdate u) {
        row.icon(R.drawable.ic_campaign, Tone.GOLD).title(u.title).subtitle(u.summary)
                .meta(Dates.day(u.publishedAt)).badge(u.typeLabel, Tone.GOLD);
    }

    @Override
    protected void onItemClick(LegalUpdate u) {
        Router.openUpdate(this, u);
    }

    @Override
    protected void configure(ListController<LegalUpdate> c) {
        c.empty(R.drawable.ic_campaign, getString(R.string.updates_empty_title), null);
    }

    @Override
    protected boolean reloadOnReturn() {
        return false;
    }
}
