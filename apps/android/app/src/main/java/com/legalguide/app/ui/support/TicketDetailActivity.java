package com.legalguide.app.ui.support;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.legalguide.app.R;
import com.legalguide.app.core.net.ApiClient;
import com.legalguide.app.core.util.Dates;
import com.legalguide.app.core.util.Json;
import com.legalguide.app.core.util.Texts;
import com.legalguide.app.data.model.Ticket;
import com.legalguide.app.data.model.TicketMessage;
import com.legalguide.app.databinding.ActivityTicketDetailBinding;
import com.legalguide.app.databinding.ItemTicketMessageBinding;
import com.legalguide.app.ui.common.BaseActivity;
import com.legalguide.app.ui.common.Tone;
import com.legalguide.app.ui.common.Ui;

import java.util.ArrayList;
import java.util.List;

public class TicketDetailActivity extends BaseActivity {
    private static final String EXTRA_ID = "ticket_id";

    private ActivityTicketDetailBinding b;
    private String id;
    private final List<TicketMessage> messages = new ArrayList<>();

    public static void start(Context c, String id) {
        c.startActivity(new Intent(c, TicketDetailActivity.class).putExtra(EXTRA_ID, id));
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!ensureSession()) return;
        b = ActivityTicketDetailBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        applyInsets(b.getRoot());
        setupToolbar(b.toolbar, R.string.ticket_title, true);
        id = getIntent().getStringExtra(EXTRA_ID);
        LinearLayoutManager lm = new LinearLayoutManager(this);
        lm.setStackFromEnd(true);
        b.list.setLayoutManager(lm);
        b.list.setAdapter(adapter);
        b.send.setOnClickListener(v -> reply());
        load();
    }

    private void load() {
        if (messages.isEmpty()) b.state.loading();
        call(ApiClient.api().ticket(id), t -> {
            if (t == null) return;
            b.state.hide();
            bind(t);
        }, e -> {
            if (messages.isEmpty()) b.state.error(e, this::load);
            else showError(e);
        });
    }

    private void bind(Ticket t) {
        b.toolbar.setTitle(t.subject);
        b.toolbar.setSubtitle("#" + t.id);
        Ui.text(b.status, t.statusLabel);
        if (!Texts.blank(t.statusLabel)) Tone.forStatus(t.status).badge(b.status);
        boolean closed = "CLOSED".equals(t.status) || "RESOLVED".equals(t.status);
        b.composer.setVisibility(closed ? View.GONE : View.VISIBLE);
        b.closedNote.setVisibility(closed ? View.VISIBLE : View.GONE);
        messages.clear();
        if (t.messages != null) messages.addAll(t.messages);
        adapter.notifyDataSetChanged();
        if (!messages.isEmpty()) b.list.scrollToPosition(messages.size() - 1);
    }

    private void reply() {
        String body = Texts.trim(b.input.getText());
        if (body.isEmpty()) return;
        b.send.setEnabled(false);
        call(ApiClient.api().replyTicket(id, Json.map("body", body)), m -> {
            b.send.setEnabled(true);
            b.input.setText("");
            Ui.hideKeyboard(b.input);
            if (m != null) {
                messages.add(m);
                adapter.notifyItemInserted(messages.size() - 1);
                b.list.scrollToPosition(messages.size() - 1);
            }
        }, e -> {
            b.send.setEnabled(true);
            showError(e);
        });
    }

    private final RecyclerView.Adapter<RecyclerView.ViewHolder> adapter = new RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            ItemTicketMessageBinding mb = ItemTicketMessageBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
            mb.getRoot().setTag(mb);
            return new RecyclerView.ViewHolder(mb.getRoot()) {};
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder h, int position) {
            ItemTicketMessageBinding mb = (ItemTicketMessageBinding) h.itemView.getTag();
            TicketMessage m = messages.get(position);
            mb.getRoot().setGravity(m.fromSupport ? android.view.Gravity.START : android.view.Gravity.END);
            mb.bubble.setBackgroundResource(m.fromSupport ? R.drawable.bg_bubble_ai : R.drawable.bg_bubble_user);
            mb.body.setTextColor(getColor(m.fromSupport ? R.color.bubble_ai_text : R.color.bubble_user_text));
            mb.body.setText(m.body);
            mb.meta.setText(Texts.joinNonBlank(" • ", m.fromSupport ? Texts.orDash(m.authorName) : getString(R.string.ticket_you),
                    Dates.relative(m.createdAt)));
        }

        @Override
        public int getItemCount() {
            return messages.size();
        }
    };
}
