package com.legalguide.app.ui.contracts;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.Nullable;

import com.legalguide.app.R;
import com.legalguide.app.core.net.ApiClient;
import com.legalguide.app.core.session.Session;
import com.legalguide.app.core.util.Json;
import com.legalguide.app.core.util.Texts;
import com.legalguide.app.data.model.ContractTemplate;
import com.legalguide.app.data.model.LegalCase;
import com.legalguide.app.data.model.Option;
import com.legalguide.app.data.model.Wallet;
import com.legalguide.app.databinding.ActivityContractWizardBinding;
import com.legalguide.app.ui.common.BaseActivity;
import com.legalguide.app.ui.common.Dialogs;
import com.legalguide.app.ui.common.Ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Fills the template's dynamic form and asks the backend (Gemini + RAG) to draft the contract. */
public class ContractWizardActivity extends BaseActivity {
    private static final String EXTRA_ID = "template_id";

    private ActivityContractWizardBinding b;
    @Nullable private ContractTemplate template;
    private final List<Option> cases = new ArrayList<>();

    public static void start(Context c, String templateId) {
        c.startActivity(new Intent(c, ContractWizardActivity.class).putExtra(EXTRA_ID, templateId));
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!ensureSession()) return;
        b = ActivityContractWizardBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        applyInsets(b.getRoot());
        setupToolbar(b.appBar.toolbar, R.string.contract_wizard_title, true);
        b.generate.setOnClickListener(v -> generate());
        b.generate.setEnabled(false);
        load();
        call(ApiClient.api().cases(null, null, null, null), page -> {
            cases.clear();
            cases.add(new Option("", getString(R.string.contract_no_case)));
            if (page != null && page.items != null) for (LegalCase c : page.items) cases.add(new Option(c.id, c.toString()));
            Ui.dropdown(b.caseLink, cases);
        }, e -> Ui.show(b.caseLinkLayout, false));
    }

    private void load() {
        b.state.loading();
        call(ApiClient.api().contractTemplate(getIntent().getStringExtra(EXTRA_ID)), t -> {
            if (t == null) return;
            template = t;
            b.state.hide();
            b.appBar.toolbar.setTitle(t.name);
            b.name.setText(t.name);
            Ui.text(b.description, t.description);
            Ui.text(b.category, t.categoryLabel);
            Wallet w = Session.get().wallet();
            String cost = w != null && w.freeServiceUses > 0 ? getString(R.string.cost_free_use)
                    : t.credits > 0 ? getString(R.string.contract_cost, t.credits) : null;
            Ui.text(b.cost, cost);
            b.form.setFields(t.fields, null);
            b.generate.setEnabled(true);
        }, e -> b.state.error(e, this::load));
    }

    private void generate() {
        if (template == null || !b.form.validate()) return;
        Dialogs.confirmCost(this, getString(R.string.contract_generate), template.credits, this::doGenerate);
    }

    private void doGenerate() {
        String caseId = Ui.optionValue(cases, b.caseLink.getText());
        Map<String, Object> body = Json.map("templateId", template.id, "values", b.form.values(),
                "caseId", Texts.blank(caseId) ? null : caseId,
                "instructions", Texts.nullIfBlank(b.instructions.getText()));
        Ui.loading(b.generate, true);
        b.generating.setVisibility(android.view.View.VISIBLE);
        call(ApiClient.api().generateContract(body), res -> {
            Ui.loading(b.generate, false);
            b.generating.setVisibility(android.view.View.GONE);
            if (res == null || res.contract == null) return;
            Session.get().updateWallet(res.wallet);
            ContractViewActivity.start(this, res.contract.id);
            finish();
        }, e -> {
            Ui.loading(b.generate, false);
            b.generating.setVisibility(android.view.View.GONE);
            showError(e);
        });
    }
}
