package com.legalguide.app.ui.clients;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.Nullable;

import com.legalguide.app.R;
import com.legalguide.app.core.net.ApiClient;
import com.legalguide.app.core.session.Session;
import com.legalguide.app.core.util.Intents;
import com.legalguide.app.core.util.Money;
import com.legalguide.app.core.util.Texts;
import com.legalguide.app.data.model.Client;
import com.legalguide.app.data.model.LegalCase;
import com.legalguide.app.databinding.ActivityClientDetailBinding;
import com.legalguide.app.databinding.ItemRowBinding;
import com.legalguide.app.ui.cases.CaseDetailActivity;
import com.legalguide.app.ui.cases.CaseFormActivity;
import com.legalguide.app.ui.cases.CasesFragment;
import com.legalguide.app.ui.common.BaseActivity;
import com.legalguide.app.ui.common.Dialogs;
import com.legalguide.app.ui.common.RowAdapter;
import com.legalguide.app.ui.common.Ui;

public class ClientDetailActivity extends BaseActivity {
    private static final String EXTRA_ID = "client_id";
    private ActivityClientDetailBinding b;
    private String id;
    @Nullable private Client client;

    public static void start(Context c, String clientId) {
        c.startActivity(new Intent(c, ClientDetailActivity.class).putExtra(EXTRA_ID, clientId));
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!ensureSession()) return;
        b = ActivityClientDetailBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        applyInsets(b.getRoot());
        setupToolbar(b.appBar.toolbar, R.string.client_title, true);
        id = getIntent().getStringExtra(EXTRA_ID);

        b.appBar.toolbar.inflateMenu(R.menu.menu_edit_delete);
        b.appBar.toolbar.getMenu().findItem(R.id.action_edit).setVisible(Session.get().can("clients.update"));
        b.appBar.toolbar.getMenu().findItem(R.id.action_delete).setVisible(Session.get().can("clients.delete"));
        b.appBar.toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_edit) ClientFormActivity.edit(this, id);
            else if (item.getItemId() == R.id.action_delete) Dialogs.confirmDelete(this, getString(R.string.kind_client), () ->
                    call(ApiClient.api().deleteClient(id), ok -> finish()));
            return true;
        });
        b.call.setOnClickListener(v -> { if (client != null) Intents.dial(this, client.phone); });
        b.whatsapp.setOnClickListener(v -> { if (client != null) Intents.whatsapp(this, client.phone, null); });
        b.email.setOnClickListener(v -> { if (client != null) Intents.email(this, client.email, null); });
        b.newCase.setOnClickListener(v -> CaseFormActivity.startForClient(this, id));
        Ui.show(b.newCase, Session.get().can("cases.create"));
        b.state.loading();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (id != null) load();
    }

    private void load() {
        call(ApiClient.api().client(id), c -> {
            if (c == null) return;
            client = c;
            b.state.hide();
            bind(c);
        }, e -> {
            if (client == null) b.state.error(e, this::load);
            else showError(e);
        });
    }

    private void bind(Client c) {
        b.avatar.setText(Texts.initials(c.name));
        b.name.setText(c.name);
        Ui.text(b.contact, Texts.joinNonBlank(" • ", c.phone, c.email));
        b.call.setEnabled(!Texts.blank(c.phone));
        b.whatsapp.setEnabled(!Texts.blank(c.phone));
        b.email.setEnabled(!Texts.blank(c.email));

        b.info.removeAllViews();
        Ui.kv(b.info, getString(R.string.field_phone), c.phone);
        Ui.kv(b.info, getString(R.string.field_email), c.email);
        Ui.kv(b.info, getString(R.string.field_address), c.address);
        Ui.show(b.notesCard, !Texts.blank(c.notes));
        b.notes.setText(c.notes);

        boolean fees = c.feesTotal != null && c.feesTotal > 0;
        Ui.show(b.feesCard, fees);
        if (fees) {
            double paid = c.feesPaid != null ? c.feesPaid : 0;
            b.feesTotal.setText(Money.format(c.feesTotal, "EGP"));
            b.feesPaid.setText(getString(R.string.client_fees_paid, Money.format(paid, "EGP")));
            b.feesRemaining.setText(getString(R.string.client_fees_remaining, Money.format(Math.max(0, c.feesTotal - paid), "EGP")));
            b.feesProgress.setProgress((int) Math.round(Math.min(100, paid * 100 / c.feesTotal)));
        }

        b.cases.removeAllViews();
        boolean hasCases = c.cases != null && !c.cases.isEmpty();
        b.casesEmpty.setVisibility(hasCases ? View.GONE : View.VISIBLE);
        if (hasCases) {
            for (LegalCase lc : c.cases) {
                ItemRowBinding rb = ItemRowBinding.inflate(getLayoutInflater(), b.cases, true);
                RowAdapter.Row r = new RowAdapter.Row(rb);
                r.reset();
                CasesFragment.bindCase(r, lc);
                rb.getRoot().setOnClickListener(v -> CaseDetailActivity.start(this, lc.id));
            }
        }
    }
}
