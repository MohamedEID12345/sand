package com.legalguide.app.ui.hearings;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.Nullable;

import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.timepicker.MaterialTimePicker;
import com.google.android.material.timepicker.TimeFormat;
import com.legalguide.app.R;
import com.legalguide.app.core.net.ApiClient;
import com.legalguide.app.core.notify.ReminderScheduler;
import com.legalguide.app.core.session.Session;
import com.legalguide.app.core.util.Dates;
import com.legalguide.app.core.util.Json;
import com.legalguide.app.core.util.Texts;
import com.legalguide.app.data.model.CaseMeta;
import com.legalguide.app.data.model.Hearing;
import com.legalguide.app.data.model.Option;
import com.legalguide.app.databinding.ActivityHearingFormBinding;
import com.legalguide.app.ui.cases.CaseDetailActivity;
import com.legalguide.app.ui.common.BaseActivity;
import com.legalguide.app.ui.common.Dialogs;
import com.legalguide.app.ui.common.Ui;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Add a hearing to a case, or update one (postponement, decision, status). */
public class HearingFormActivity extends BaseActivity {
    private static final String EXTRA_CASE_ID = "case_id", EXTRA_ID = "hearing_id";

    private ActivityHearingFormBinding b;
    @Nullable private String caseId;
    @Nullable private String hearingId;
    @Nullable private Hearing editing;
    @Nullable private LocalDate date;
    private LocalTime time = LocalTime.of(9, 0);
    private final List<Option> statuses = new ArrayList<>();

    public static void create(Context c, String caseId) {
        c.startActivity(new Intent(c, HearingFormActivity.class).putExtra(EXTRA_CASE_ID, caseId));
    }

    public static void edit(Context c, String hearingId) {
        c.startActivity(new Intent(c, HearingFormActivity.class).putExtra(EXTRA_ID, hearingId));
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!ensureSession()) return;
        b = ActivityHearingFormBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        applyInsets(b.getRoot());
        caseId = getIntent().getStringExtra(EXTRA_CASE_ID);
        hearingId = getIntent().getStringExtra(EXTRA_ID);
        setupToolbar(b.appBar.toolbar, hearingId == null ? R.string.hearing_new : R.string.hearing_edit, true);

        CaseMeta meta = Session.get().caseMeta();
        if (meta != null && meta.hearingStatuses != null) statuses.addAll(meta.hearingStatuses);
        Ui.dropdown(b.status, statuses);
        boolean isEdit = hearingId != null;
        Ui.show(b.statusLayout, isEdit && !statuses.isEmpty());
        Ui.show(b.decisionLayout, isEdit);

        b.date.setOnClickListener(v -> pickDate());
        b.dateLayout.setEndIconOnClickListener(v -> pickDate());
        b.time.setOnClickListener(v -> pickTime());
        b.timeLayout.setEndIconOnClickListener(v -> pickTime());
        b.time.setText(format(time));
        b.save.setOnClickListener(v -> save());

        if (isEdit) {
            b.appBar.toolbar.inflateMenu(R.menu.menu_hearing);
            b.appBar.toolbar.setOnMenuItemClickListener(item -> {
                if (item.getItemId() == R.id.action_open_case && editing != null && editing.caseId != null) {
                    CaseDetailActivity.start(this, editing.caseId);
                } else if (item.getItemId() == R.id.action_delete) {
                    Dialogs.confirmDelete(this, getString(R.string.kind_hearing), () ->
                            call(ApiClient.api().deleteHearing(hearingId), ok -> {
                                ReminderScheduler.cancel(this, hearingId);
                                finish();
                            }));
                }
                return true;
            });
            b.save.setEnabled(false);
            call(ApiClient.api().hearing(hearingId), h -> {
                editing = h;
                b.save.setEnabled(true);
                fill();
            });
        }
    }

    private void fill() {
        Hearing h = editing;
        if (h == null) return;
        caseId = h.caseId;
        ZonedDateTime z = Dates.parse(h.hearingDate);
        if (z != null) {
            date = z.toLocalDate();
            if (!Dates.isDateOnly(h.hearingDate)) time = z.toLocalTime().withSecond(0).withNano(0);
            b.date.setText(Dates.formatLocalDate(date));
            b.time.setText(format(time));
        }
        Ui.text(b.caseLabel, Texts.blank(h.caseTitle) ? h.caseNumber : h.caseTitle);
        b.court.setText(h.court);
        b.circuit.setText(h.circuit);
        b.purpose.setText(h.purpose);
        b.notes.setText(h.notes);
        b.decision.setText(h.decision);
        Ui.selectOption(b.status, statuses, h.status);
    }

    private void pickDate() {
        MaterialDatePicker<Long> p = MaterialDatePicker.Builder.datePicker()
                .setTitleText(R.string.hearing_date)
                .setSelection(date != null ? Dates.toPickerMillis(date) : MaterialDatePicker.todayInUtcMilliseconds())
                .build();
        p.addOnPositiveButtonClickListener(ms -> {
            date = Dates.fromPickerMillis(ms);
            b.date.setText(Dates.formatLocalDate(date));
            b.dateLayout.setError(null);
        });
        p.show(getSupportFragmentManager(), "hearing-date");
    }

    private void pickTime() {
        MaterialTimePicker p = new MaterialTimePicker.Builder()
                .setTimeFormat(TimeFormat.CLOCK_12H)
                .setHour(time.getHour())
                .setMinute(time.getMinute())
                .setTitleText(R.string.hearing_time)
                .build();
        p.addOnPositiveButtonClickListener(v -> {
            time = LocalTime.of(p.getHour(), p.getMinute());
            b.time.setText(format(time));
        });
        p.show(getSupportFragmentManager(), "hearing-time");
    }

    private static String format(LocalTime t) {
        return java.time.format.DateTimeFormatter.ofPattern("h:mm a", Dates.AR).format(t);
    }

    private void save() {
        if (date == null) {
            b.dateLayout.setError(getString(R.string.error_required));
            return;
        }
        String when = Dates.isoInstant(date.atTime(time).atZone(Dates.zone()));
        Map<String, Object> body = Json.map("hearingDate", when,
                "court", Texts.nullIfBlank(b.court.getText()),
                "circuit", Texts.nullIfBlank(b.circuit.getText()),
                "purpose", Texts.nullIfBlank(b.purpose.getText()),
                "notes", Texts.nullIfBlank(b.notes.getText()));
        if (hearingId != null) {
            body.put("status", Ui.optionValue(statuses, b.status.getText()));
            String decision = Texts.nullIfBlank(b.decision.getText());
            if (decision != null) body.put("decision", decision);
        }
        body.values().removeIf(v -> v == null);
        Ui.loading(b.save, true);
        if (hearingId == null) {
            if (caseId == null) {
                finish();
                return;
            }
            call(ApiClient.api().createHearing(caseId, body), h -> done(h), e -> {
                Ui.loading(b.save, false);
                showError(e);
            });
        } else {
            call(ApiClient.api().updateHearing(hearingId, body), h -> done(h), e -> {
                Ui.loading(b.save, false);
                showError(e);
            });
        }
    }

    private void done(@Nullable Hearing h) {
        Ui.loading(b.save, false);
        if (h != null) ReminderScheduler.schedule(this, h);
        toast(R.string.saved);
        finish();
    }

    @Override
    protected void onPostCreate(@Nullable Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        if (b != null) b.caseLabel.setVisibility(Texts.blank(b.caseLabel.getText()) ? View.GONE : View.VISIBLE);
    }
}
