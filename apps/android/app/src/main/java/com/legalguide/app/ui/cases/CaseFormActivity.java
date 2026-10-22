package com.legalguide.app.ui.cases;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;

import com.legalguide.app.R;
import com.legalguide.app.core.net.ApiClient;
import com.legalguide.app.core.session.Session;
import com.legalguide.app.core.util.Json;
import com.legalguide.app.core.util.Texts;
import com.legalguide.app.data.model.CaseMeta;
import com.legalguide.app.data.model.CaseType;
import com.legalguide.app.data.model.Client;
import com.legalguide.app.data.model.LegalCase;
import com.legalguide.app.data.model.Option;
import com.legalguide.app.databinding.ActivityCaseFormBinding;
import com.legalguide.app.ui.clients.ClientFormActivity;
import com.legalguide.app.ui.common.BaseActivity;
import com.legalguide.app.ui.common.Ui;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Create / edit a case. Case types and their extra fields are defined by the admin. */
public class CaseFormActivity extends BaseActivity {
    private static final String EXTRA_ID = "case_id", EXTRA_CLIENT_ID = "client_id";

    private ActivityCaseFormBinding b;
    @Nullable private String caseId;
    @Nullable private LegalCase editing;
    @Nullable private CaseMeta meta;
    private final List<Option> typeOptions = new ArrayList<>();
    private final List<Option> clientOptions = new ArrayList<>();
    private final List<Option> statusOptions = new ArrayList<>();
    private final List<Option> priorityOptions = new ArrayList<>();
    @Nullable private String selectedTypeId;
    @Nullable private String preselectClientId;

    private final ActivityResultLauncher<Intent> newClient = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), r -> {
                if (r.getResultCode() == Activity.RESULT_OK && r.getData() != null) {
                    String id = r.getData().getStringExtra(ClientFormActivity.RESULT_ID);
                    String name = r.getData().getStringExtra(ClientFormActivity.RESULT_NAME);
                    if (id != null) {
                        clientOptions.add(0, new Option(id, name));
                        Ui.dropdown(b.client, clientOptions);
                        b.client.setText(name, false);
                    }
                }
            });

    public static void edit(Context c, String caseId) {
        c.startActivity(new Intent(c, CaseFormActivity.class).putExtra(EXTRA_ID, caseId));
    }

    public static void startForClient(Context c, String clientId) {
        c.startActivity(new Intent(c, CaseFormActivity.class).putExtra(EXTRA_CLIENT_ID, clientId));
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!ensureSession()) return;
        b = ActivityCaseFormBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        applyInsets(b.getRoot());
        caseId = getIntent().getStringExtra(EXTRA_ID);
        preselectClientId = getIntent().getStringExtra(EXTRA_CLIENT_ID);
        setupToolbar(b.appBar.toolbar, caseId == null ? R.string.case_new : R.string.case_edit, true);
        b.year.setText(String.valueOf(LocalDate.now().getYear()));

        b.caseType.setOnItemClickListener((p, v, pos, id) -> {
            selectedTypeId = typeOptions.get(pos).value;
            b.caseTypeLayout.setError(null);
            renderCustomFields(null);
        });
        b.addClient.setOnClickListener(v -> newClient.launch(new Intent(this, ClientFormActivity.class)));
        b.save.setOnClickListener(v -> save());

        meta = Session.get().caseMeta();
        if (meta != null) bindMeta();
        call(ApiClient.api().caseMeta(), m -> {
            Session.get().setCaseMeta(m);
            meta = m;
            bindMeta();
        }, e -> { if (meta == null) showError(e); });
        loadClients();
        if (caseId != null) {
            b.save.setEnabled(false);
            call(ApiClient.api().legalCase(caseId), c -> {
                editing = c;
                b.save.setEnabled(true);
                fill();
            });
        }
    }

    private void bindMeta() {
        if (meta == null) return;
        typeOptions.clear();
        if (meta.caseTypes != null) for (CaseType t : meta.caseTypes) typeOptions.add(new Option(t.id, t.name));
        Ui.dropdown(b.caseType, typeOptions);
        statusOptions.clear();
        if (meta.statuses != null) statusOptions.addAll(meta.statuses);
        Ui.dropdown(b.status, statusOptions);
        priorityOptions.clear();
        if (meta.priorities != null) priorityOptions.addAll(meta.priorities);
        Ui.dropdown(b.priority, priorityOptions);
        if (editing == null && !statusOptions.isEmpty() && Texts.blank(b.status.getText())) b.status.setText(statusOptions.get(0).label, false);
        fill();
    }

    private void loadClients() {
        call(ApiClient.api().clients(null, null), page -> {
            clientOptions.clear();
            if (page != null && page.items != null) for (Client c : page.items) clientOptions.add(new Option(c.id, c.name));
            Ui.dropdown(b.client, clientOptions);
            if (preselectClientId != null) Ui.selectOption(b.client, clientOptions, preselectClientId);
            fill();
        });
    }

    private void fill() {
        LegalCase c = editing;
        if (c == null || meta == null) return;
        selectedTypeId = c.caseTypeId;
        Ui.selectOption(b.caseType, typeOptions, c.caseTypeId);
        b.caseNumber.setText(c.caseNumber);
        if (c.year != null) b.year.setText(String.valueOf(c.year));
        b.court.setText(c.court);
        b.circuit.setText(c.circuit);
        if (c.clientId != null) {
            boolean known = false;
            for (Option o : clientOptions) known |= c.clientId.equals(o.value);
            if (!known && c.clientName != null) {
                clientOptions.add(new Option(c.clientId, c.clientName));
                Ui.dropdown(b.client, clientOptions);
            }
            Ui.selectOption(b.client, clientOptions, c.clientId);
        }
        b.opponent.setText(c.opponent);
        Ui.selectOption(b.status, statusOptions, c.status);
        Ui.selectOption(b.priority, priorityOptions, c.priority);
        b.description.setText(c.description);
        renderCustomFields(c.customFields);
    }

    private void renderCustomFields(@Nullable Map<String, Object> values) {
        CaseType type = null;
        if (meta != null && meta.caseTypes != null && selectedTypeId != null) {
            for (CaseType t : meta.caseTypes) if (selectedTypeId.equals(t.id)) type = t;
        }
        b.customFields.setFields(type != null ? type.fields : null, values);
        Ui.show(b.customTitle, !b.customFields.isEmptyForm());
    }

    private void save() {
        b.caseTypeLayout.setError(null);
        b.clientLayout.setError(null);
        b.yearLayout.setError(null);
        boolean ok = true;
        String typeId = Ui.optionValue(typeOptions, b.caseType.getText());
        if (typeId == null) {
            b.caseTypeLayout.setError(getString(R.string.error_required));
            ok = false;
        }
        String clientId = Ui.optionValue(clientOptions, b.client.getText());
        if (clientId == null) {
            b.clientLayout.setError(getString(R.string.error_required));
            ok = false;
        }
        Integer year = null;
        String y = Texts.asciiDigits(Texts.trim(b.year.getText()));
        if (!y.isEmpty()) {
            try {
                year = Integer.parseInt(y);
                if (year < 1900 || year > LocalDate.now().getYear() + 1) throw new NumberFormatException();
            } catch (NumberFormatException e) {
                b.yearLayout.setError(getString(R.string.error_year));
                ok = false;
            }
        }
        if (!b.customFields.validate()) ok = false;
        if (!ok) return;

        Map<String, Object> body = Json.map(
                "caseTypeId", typeId,
                "caseNumber", Texts.nullIfBlank(Texts.asciiDigits(Texts.trim(b.caseNumber.getText()))),
                "year", year,
                "court", Texts.nullIfBlank(b.court.getText()),
                "circuit", Texts.nullIfBlank(b.circuit.getText()),
                "clientId", clientId,
                "opponent", Texts.nullIfBlank(b.opponent.getText()),
                "status", Ui.optionValue(statusOptions, b.status.getText()),
                "priority", Ui.optionValue(priorityOptions, b.priority.getText()),
                "description", Texts.nullIfBlank(b.description.getText()),
                "customFields", b.customFields.values());
        Ui.loading(b.save, true);
        if (caseId == null) {
            call(ApiClient.api().createCase(body), c -> {
                Ui.loading(b.save, false);
                if (c != null) CaseDetailActivity.start(this, c.id);
                finish();
            }, e -> {
                Ui.loading(b.save, false);
                showError(e);
            });
        } else {
            call(ApiClient.api().updateCase(caseId, body), c -> {
                Ui.loading(b.save, false);
                toast(R.string.saved);
                finish();
            }, e -> {
                Ui.loading(b.save, false);
                showError(e);
            });
        }
    }
}
