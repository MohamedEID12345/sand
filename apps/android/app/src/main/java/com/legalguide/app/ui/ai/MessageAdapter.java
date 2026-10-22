package com.legalguide.app.ui.ai;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.chip.Chip;
import com.legalguide.app.R;
import com.legalguide.app.core.util.Dates;
import com.legalguide.app.core.util.Md;
import com.legalguide.app.core.util.Texts;
import com.legalguide.app.data.model.AiMessage;
import com.legalguide.app.data.model.Attachment;
import com.legalguide.app.data.model.Citation;
import com.legalguide.app.databinding.ItemMsgAiBinding;
import com.legalguide.app.databinding.ItemMsgTypingBinding;
import com.legalguide.app.databinding.ItemMsgUserBinding;
import com.legalguide.app.ui.common.Ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Chat bubbles: user, assistant (markdown + verified citations), typing indicator. */
public class MessageAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    public interface Listener {
        void onCitation(Citation c);
        void onRetry(AiMessage m);
        void onCopy(AiMessage m);
        void onShare(AiMessage m);
    }

    private static final int USER = 0, AI = 1, TYPING = 2;
    private static final Object TYPING_ROW = new Object();

    private final Listener listener;
    private List<Object> rows = new ArrayList<>();

    public MessageAdapter(Listener listener) {
        this.listener = listener;
    }

    public void submit(List<AiMessage> messages, boolean typing) {
        List<Object> next = new ArrayList<>(messages);
        if (typing) next.add(TYPING_ROW);
        List<Object> old = rows;
        DiffUtil.DiffResult diff = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override public int getOldListSize() { return old.size(); }
            @Override public int getNewListSize() { return next.size(); }

            @Override
            public boolean areItemsTheSame(int o, int n) {
                Object a = old.get(o), b = next.get(n);
                if (a == TYPING_ROW || b == TYPING_ROW) return a == b;
                return Objects.equals(((AiMessage) a).id, ((AiMessage) b).id) || a == b;
            }

            @Override
            public boolean areContentsTheSame(int o, int n) {
                Object a = old.get(o), b = next.get(n);
                if (a == TYPING_ROW) return true;
                AiMessage x = (AiMessage) a, y = (AiMessage) b;
                return x == y ? x.localState == stateOf(o) : Objects.equals(x.id, y.id) && Objects.equals(x.content, y.content)
                        && x.localState == y.localState;
            }
        });
        rows = next;
        states.clear();
        for (Object r : rows) states.add(r instanceof AiMessage ? ((AiMessage) r).localState : -1);
        diff.dispatchUpdatesTo(this);
    }

    private final List<Integer> states = new ArrayList<>();

    private int stateOf(int oldPos) {
        return oldPos < states.size() ? states.get(oldPos) : -1;
    }

    @Override
    public int getItemViewType(int position) {
        Object r = rows.get(position);
        if (r == TYPING_ROW) return TYPING;
        return ((AiMessage) r).isUser() ? USER : AI;
    }

    @Override
    public int getItemCount() {
        return rows.size();
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inf = LayoutInflater.from(parent.getContext());
        if (viewType == USER) return new UserHolder(ItemMsgUserBinding.inflate(inf, parent, false));
        if (viewType == AI) return new AiHolder(ItemMsgAiBinding.inflate(inf, parent, false));
        return new TypingHolder(ItemMsgTypingBinding.inflate(inf, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Object r = rows.get(position);
        if (holder instanceof UserHolder) bindUser(((UserHolder) holder).b, (AiMessage) r);
        else if (holder instanceof AiHolder) bindAi(((AiHolder) holder).b, (AiMessage) r);
    }

    private void bindUser(ItemMsgUserBinding b, AiMessage m) {
        b.msgText.setText(m.content);
        List<String> names = new ArrayList<>();
        if (m.attachments != null) for (Attachment a : m.attachments) names.add("📎 " + a.name);
        Ui.text(b.msgAttachments, Texts.join(names, "\n"));
        b.msgBubble.setAlpha(m.localState == AiMessage.STATE_PENDING ? 0.7f : 1f);
        if (m.localState == AiMessage.STATE_FAILED) {
            b.msgStatus.setVisibility(View.VISIBLE);
            b.msgStatus.setText(R.string.chat_failed_retry);
            b.msgStatus.setOnClickListener(v -> listener.onRetry(m));
        } else {
            b.msgStatus.setVisibility(View.GONE);
            b.msgStatus.setOnClickListener(null);
        }
    }

    private void bindAi(ItemMsgAiBinding b, AiMessage m) {
        Md.render(b.msgText, m.content);
        b.msgCitations.removeAllViews();
        boolean hasCitations = m.citations != null && !m.citations.isEmpty();
        b.msgCitationsBox.setVisibility(hasCitations ? View.VISIBLE : View.GONE);
        if (hasCitations) {
            for (Citation c : m.citations) {
                Chip chip = Ui.chip(b.msgCitations, citationLabel(c), false);
                chip.setChipIconResource(c.verified ? R.drawable.ic_verified : R.drawable.ic_warning);
                chip.setChipIconVisible(true);
                chip.setChipIconTintResource(c.verified ? R.color.tone_success : R.color.tone_warning);
                chip.setOnClickListener(v -> listener.onCitation(c));
            }
        }
        String meta = Dates.time(m.createdAt);
        if (m.usedFreeUse) meta = Texts.joinNonBlank(" • ", meta, b.getRoot().getContext().getString(R.string.cost_free_use_done));
        else if (m.creditsCharged > 0) meta = Texts.joinNonBlank(" • ", meta,
                b.getRoot().getContext().getString(R.string.cost_charged, m.creditsCharged));
        b.msgMeta.setText(meta);
        b.msgCopy.setOnClickListener(v -> listener.onCopy(m));
        b.msgShare.setOnClickListener(v -> listener.onShare(m));
    }

    static String citationLabel(Citation c) {
        if (!Texts.blank(c.law) && !Texts.blank(c.article)) return c.law + " — م " + c.article;
        if (!Texts.blank(c.judgment)) return c.judgment;
        if (!Texts.blank(c.title)) return c.page != null ? c.title + " — ص " + c.page : c.title;
        return "مصدر";
    }

    static final class UserHolder extends RecyclerView.ViewHolder {
        final ItemMsgUserBinding b;
        UserHolder(ItemMsgUserBinding b) { super(b.getRoot()); this.b = b; }
    }

    static final class AiHolder extends RecyclerView.ViewHolder {
        final ItemMsgAiBinding b;
        AiHolder(ItemMsgAiBinding b) { super(b.getRoot()); this.b = b; }
    }

    static final class TypingHolder extends RecyclerView.ViewHolder {
        TypingHolder(ItemMsgTypingBinding b) { super(b.getRoot()); }
    }
}
