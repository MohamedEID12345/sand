package com.legalguide.app.ui.cases;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.legalguide.app.R;
import com.legalguide.app.core.net.ApiClient;
import com.legalguide.app.core.session.Session;
import com.legalguide.app.core.util.Dates;
import com.legalguide.app.core.util.Json;
import com.legalguide.app.core.util.Texts;
import com.legalguide.app.data.model.CaseMeta;
import com.legalguide.app.data.model.CaseType;
import com.legalguide.app.data.model.Deadline;
import com.legalguide.app.data.model.FormField;
import com.legalguide.app.data.model.LegalCase;
import com.legalguide.app.data.model.Option;
import com.legalguide.app.databinding.FragmentCaseOverviewBinding;
import com.legalguide.app.databinding.ItemRowBinding;
import com.legalguide.app.ui.ai.AiModes;
import com.legalguide.app.ui.ai.ChatActivity;
import com.legalguide.app.ui.clients.ClientDetailActivity;
import com.legalguide.app.ui.common.BaseFragment;
import com.legalguide.app.ui.common.Dialogs;
import com.legalguide.app.ui.common.RowAdapter;
import com.legalguide.app.ui.common.Tone;
import com.legalguide.app.ui.common.Ui;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public class CaseOverviewFragment extends BaseFragment implements CaseDetailActivity.Tab {
    private FragmentCaseOverviewBinding b;
    private CaseViewModel vm;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        b = FragmentCaseOverviewBinding.inflate(inflater, container, false);
        return b.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        vm = new ViewModelProvider(requireActivity()).get(CaseViewModel.class);
        vm.legalCase().observe(getViewLifecycleOwner(), this::bind);
        b.computeDeadlines.setOnClickListener(v -> computeDialog());
        b.askAi.setOnClickListener(v -> ChatActivity.start(requireContext(), AiModes.QUESTION.code, vm.caseId()));
        loadDeadlines();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        b = null;
    }

    private void bind(@Nullable LegalCase c) {
        if (c == null || b == null) return;
        b.details.removeAllViews();
        Ui.kv(b.details, getString(R.string.case_type), c.caseTypeName);
        Ui.kv(b.details, getString(R.string.case_number), c.caseNumber);
        Ui.kv(b.details, getString(R.string.case_year), c.year == null ? null : String.valueOf(c.year));
        Ui.kv(b.details, getString(R.string.case_court), c.court);
        Ui.kv(b.details, getString(R.string.case_circuit), c.circuit);
        android.widget.TextView client = Ui.kv(b.details, getString(R.string.case_client), c.clientName);
        if (c.clientId != null) {
            client.setTextColor(androidx.core.content.ContextCompat.getColor(requireContext(), R.color.lg_primary));
            client.setOnClickListener(v -> ClientDetailActivity.start(requireContext(), c.clientId));
        }
        Ui.kv(b.details, getString(R.string.case_opponent), c.opponent);
        Ui.kv(b.details, getString(R.string.case_lawyers), Texts.join(c.lawyers, "، "));
        Ui.kv(b.details, getString(R.string.case_created), Dates.day(c.createdAt));

        Ui.show(b.descriptionCard, !Texts.blank(c.description));
        b.description.setText(c.description);

        b.customFields.removeAllViews();
        boolean hasCustom = c.customFields != null && !c.customFields.isEmpty();
        Ui.show(b.customCard, hasCustom);
        if (hasCustom) {
            CaseType type = findType(c.caseTypeId);
            for (Map.Entry<String, Object> e : c.customFields.entrySet()) {
                FormField f = findField(type, e.getKey());
                Ui.kv(b.customFields, f != null ? f.label : e.getKey(), format(f, e.getValue()));
            }
        }

        if (c.nextHearingAt != null) {
            b.nextHearing.setText(Dates.dayTime(c.nextHearingAt));
            long d = Dates.daysUntil(c.nextHearingAt);
            b.nextHearingIn.setText(d == 0 ? getString(R.string.today) : d == 1 ? getString(R.string.tomorrow)
                    : d > 1 ? getString(R.string.in_days, d) : "");
        } else {
            b.nextHearing.setText(R.string.case_no_next_hearing);
            b.nextHearingIn.setText("");
        }
    }

    @Nullable
    private static CaseType findType(@Nullable String id) {
        CaseMeta meta = Session.get().caseMeta();
        if (meta == null || meta.caseTypes == null || id == null) return null;
        for (CaseType t : meta.caseTypes) if (id.equals(t.id)) return t;
        return null;
    }

    @Nullable
    private static FormField findField(@Nullable CaseType t, String key) {
        if (t == null || t.fields == null) return null;
        for (FormField f : t.fields) if (key.equals(f.key)) return f;
        return null;
    }

    private String format(@Nullable FormField f, @Nullable Object v) {
        if (v == null) return "—";
        if (v instanceof Boolean) return getString((Boolean) v ? R.string.yes : R.string.no);
        if (v instanceof Collection) {
            StringBuilder sb = new StringBuilder();
            for (Object o : (Collection<?>) v) {
                if (sb.length() > 0) sb.append("، ");
                sb.append(optionLabel(f, String.valueOf(o)));
            }
            return sb.toString();
        }
        String s = v instanceof Double && ((Double) v) == Math.rint((Double) v) ? String.valueOf(((Double) v).longValue()) : String.valueOf(v);
        if (f != null && "date".equals(f.type)) return Dates.day(s);
        return optionLabel(f, s);
    }

    private static String optionLabel(@Nullable FormField f, String value) {
        if (f != null && f.options != null) for (Option o : f.options) if (value.equals(o.value)) return o.label;
        return value;
    }

    // ---------- Deadlines (rules-based, computed on the server) ----------
    private void loadDeadlines() {
        if (vm.caseId() == null) return;
        call(ApiClient.api().caseDeadlines(vm.caseId()), p -> renderDeadlines(p == null ? null : p.items), e -> renderDeadlines(null));
    }

    private void renderDeadlines(@Nullable List<Deadline> list) {
        if (b == null) return;
        b.deadlines.removeAllViews();
        if (list == null || list.isEmpty()) {
            b.deadlinesEmpty.setVisibility(View.VISIBLE);
            return;
        }
        b.deadlinesEmpty.setVisibility(View.GONE);
        for (Deadline d : list) {
            ItemRowBinding rb = ItemRowBinding.inflate(getLayoutInflater(), b.deadlines, true);
            RowAdapter.Row r = new RowAdapter.Row(rb);
            r.reset();
            long days = d.daysLeft != null ? d.daysLeft : Dates.daysUntil(d.dueDate);
            Tone tone = days < 0 ? Tone.NEUTRAL : days <= 3 ? Tone.DANGER : days <= 10 ? Tone.WARNING : Tone.INFO;
            r.icon(R.drawable.ic_timer, tone).title(d.title)
                    .subtitle(Texts.joinNonBlank(" • ", d.rule, d.legalSource))
                    .meta(Dates.day(d.dueDate))
                    .badge(days < 0 ? getString(R.string.deadline_passed) : getString(R.string.in_days, days), tone);
            rb.getRoot().setOnClickListener(v -> Dialogs.info(requireContext(), d.title,
                    Texts.joinNonBlank("\n\n", d.calculation, d.legalSource, getString(R.string.deadline_disclaimer))));
        }
    }

    private void computeDialog() {
        CaseMeta meta = Session.get().caseMeta();
        List<Option> events = meta != null ? meta.deadlineEvents : null;
        if (events == null || events.isEmpty()) {
            Dialogs.info(requireContext(), getString(R.string.deadlines_title), getString(R.string.deadlines_unavailable));
            return;
        }
        String[] labels = new String[events.size()];
        for (int i = 0; i < events.size(); i++) labels[i] = events.get(i).label;
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.deadlines_pick_event)
                .setItems(labels, (d, which) -> pickDate(events.get(which)))
                .show();
    }

    private void pickDate(Option event) {
        MaterialDatePicker<Long> p = MaterialDatePicker.Builder.datePicker()
                .setTitleText(getString(R.string.deadlines_event_date, event.label))
                .setSelection(MaterialDatePicker.todayInUtcMilliseconds())
                .build();
        p.addOnPositiveButtonClickListener(ms -> {
            LocalDate date = Dates.fromPickerMillis(ms);
            call(ApiClient.api().computeDeadlines(vm.caseId(), Json.map("event", event.value, "eventDate", date.toString())),
                    page -> renderDeadlines(page == null ? null : page.items));
        });
        p.show(getChildFragmentManager(), "deadline-date");
    }

    // ---------- Tab ----------
    @Override public int fabText() { return R.string.case_ask_ai; }
    @Override public int fabIcon() { return R.drawable.ic_ai; }
    @Override public void onFab() { ChatActivity.start(requireContext(), AiModes.QUESTION.code, vm.caseId()); }
    @Override public boolean fabVisible() { return Session.get().feature("ai"); }
}
