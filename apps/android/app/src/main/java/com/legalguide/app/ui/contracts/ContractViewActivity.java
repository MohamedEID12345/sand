package com.legalguide.app.ui.contracts;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.Nullable;

import com.google.android.material.chip.Chip;
import com.legalguide.app.R;
import com.legalguide.app.core.net.ApiClient;
import com.legalguide.app.core.util.Dates;
import com.legalguide.app.core.util.Downloads;
import com.legalguide.app.core.util.Intents;
import com.legalguide.app.core.util.Md;
import com.legalguide.app.core.util.Texts;
import com.legalguide.app.data.model.Citation;
import com.legalguide.app.data.model.Contract;
import com.legalguide.app.databinding.ActivityContractViewBinding;
import com.legalguide.app.ui.ai.MessageAdapterLabels;
import com.legalguide.app.ui.cases.CaseDetailActivity;
import com.legalguide.app.ui.common.BaseActivity;
import com.legalguide.app.ui.common.Router;
import com.legalguide.app.ui.common.Ui;

/** Generated contract: review notes, text, citations, Word/PDF export. */
public class ContractViewActivity extends BaseActivity {
    private static final String EXTRA_ID = "contract_id";

    private ActivityContractViewBinding b;
    private String id;
    @Nullable private Contract contract;

    public static void start(Context c, String id) {
        c.startActivity(new Intent(c, ContractViewActivity.class).putExtra(EXTRA_ID, id));
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!ensureSession()) return;
        b = ActivityContractViewBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        applyInsets(b.getRoot());
        setupToolbar(b.appBar.toolbar, R.string.contract_title, true);
        id = getIntent().getStringExtra(EXTRA_ID);

        b.appBar.toolbar.inflateMenu(R.menu.menu_contract);
        b.appBar.toolbar.setOnMenuItemClickListener(item -> {
            if (contract == null) return true;
            int itemId = item.getItemId();
            if (itemId == R.id.action_copy) Intents.copy(this, Texts.orDash(contract.body));
            else if (itemId == R.id.action_share) Intents.shareText(this, contract.title, Texts.orDash(contract.body));
            else if (itemId == R.id.action_open_case && contract.caseId != null) CaseDetailActivity.start(this, contract.caseId);
            return true;
        });
        b.exportWord.setOnClickListener(v -> export("docx"));
        b.exportPdf.setOnClickListener(v -> export("pdf"));
        load();
    }

    private void load() {
        b.state.loading();
        call(ApiClient.api().contract(id), c -> {
            if (c == null) return;
            contract = c;
            b.state.hide();
            render(c);
        }, e -> b.state.error(e, this::load));
    }

    private void render(Contract c) {
        b.appBar.toolbar.setTitle(Texts.blank(c.title) ? getString(R.string.contract_title) : c.title);
        b.meta.setText(Texts.joinNonBlank(" • ", c.templateName, Dates.dayTime(c.createdAt), c.caseTitle));
        b.appBar.toolbar.getMenu().findItem(R.id.action_open_case).setVisible(c.caseId != null);

        boolean notes = c.reviewNotes != null && !c.reviewNotes.isEmpty();
        Ui.show(b.notesCard, notes);
        if (notes) {
            StringBuilder sb = new StringBuilder();
            for (String n : c.reviewNotes) sb.append("• ").append(n).append('\n');
            b.notes.setText(sb.toString().trim());
        }
        Md.render(b.body, c.body);

        b.citations.removeAllViews();
        boolean cit = c.citations != null && !c.citations.isEmpty();
        Ui.show(b.citationsCard, cit);
        if (cit) {
            for (Citation ct : c.citations) {
                Chip chip = Ui.chip(b.citations, MessageAdapterLabels.label(ct), false);
                chip.setChipIconResource(ct.verified ? R.drawable.ic_verified : R.drawable.ic_warning);
                chip.setChipIconVisible(true);
                chip.setOnClickListener(v -> Router.openCitation(this, ct));
            }
        }
    }

    private void export(String format) {
        View btn = "pdf".equals(format) ? b.exportPdf : b.exportWord;
        btn.setEnabled(false);
        call(ApiClient.api().exportContract(id, format), link -> {
            btn.setEnabled(true);
            String base = contract != null && !Texts.blank(contract.title) ? contract.title : "contract";
            Downloads.enqueue(this, link, base + "." + format);
        }, e -> {
            btn.setEnabled(true);
            showError(e);
        });
    }
}
