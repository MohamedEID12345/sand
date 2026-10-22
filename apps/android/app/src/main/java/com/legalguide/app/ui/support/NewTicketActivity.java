package com.legalguide.app.ui.support;

import android.os.Bundle;

import androidx.annotation.Nullable;

import com.legalguide.app.R;
import com.legalguide.app.core.net.ApiClient;
import com.legalguide.app.core.util.Json;
import com.legalguide.app.core.util.Texts;
import com.legalguide.app.data.model.Option;
import com.legalguide.app.databinding.ActivityNewTicketBinding;
import com.legalguide.app.ui.common.BaseActivity;
import com.legalguide.app.ui.common.Ui;

import java.util.ArrayList;
import java.util.List;

public class NewTicketActivity extends BaseActivity {
    private ActivityNewTicketBinding b;
    private final List<Option> categories = new ArrayList<>();
    private final List<Option> priorities = new ArrayList<>();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!ensureSession()) return;
        b = ActivityNewTicketBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        applyInsets(b.getRoot());
        setupToolbar(b.appBar.toolbar, R.string.ticket_new, true);

        priorities.add(new Option("NORMAL", getString(R.string.priority_normal)));
        priorities.add(new Option("HIGH", getString(R.string.priority_high)));
        priorities.add(new Option("URGENT", getString(R.string.priority_urgent)));
        Ui.dropdown(b.priority, priorities);
        b.priority.setText(priorities.get(0).label, false);

        call(ApiClient.api().ticketCategories(), page -> {
            categories.clear();
            if (page != null && page.items != null) categories.addAll(page.items);
            Ui.dropdown(b.category, categories);
        }, e -> Ui.show(b.categoryLayout, false));
        b.submit.setOnClickListener(v -> submit());
    }

    private void submit() {
        b.subjectLayout.setError(null);
        b.messageLayout.setError(null);
        String subject = Texts.trim(b.subject.getText());
        String message = Texts.trim(b.message.getText());
        boolean ok = true;
        if (subject.length() < 3) { b.subjectLayout.setError(getString(R.string.error_required)); ok = false; }
        if (message.length() < 10) { b.messageLayout.setError(getString(R.string.ticket_message_short)); ok = false; }
        if (!ok) return;
        Ui.loading(b.submit, true);
        call(ApiClient.api().createTicket(Json.map("subject", subject, "message", message,
                "category", Ui.optionValue(categories, b.category.getText()),
                "priority", Ui.optionValue(priorities, b.priority.getText()))), t -> {
            Ui.loading(b.submit, false);
            if (t != null) TicketDetailActivity.start(this, t.id);
            finish();
        }, e -> {
            Ui.loading(b.submit, false);
            showError(e);
        });
    }
}
