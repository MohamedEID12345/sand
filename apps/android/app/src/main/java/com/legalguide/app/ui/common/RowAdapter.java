package com.legalguide.app.ui.common;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.legalguide.app.core.util.Texts;
import com.legalguide.app.databinding.ItemRowBinding;
import com.legalguide.app.databinding.ItemSectionHeaderBinding;

import java.util.ArrayList;
import java.util.List;

/**
 * One adapter for every "list of things" screen: icon tile, title, subtitle, meta, badge, chevron.
 * Optional grouping inserts section headers when the group key changes.
 */
public class RowAdapter<T> extends RecyclerView.Adapter<RecyclerView.ViewHolder> implements PagedAdapter<T> {
    public interface Binder<T> { void bind(Row row, T item); }
    public interface OnClick<T> { void onClick(T item); }
    public interface GroupKey<T> { String of(T item); }

    private static final int TYPE_ROW = 0, TYPE_HEADER = 1;

    private static final class Header {
        final String title;
        Header(String title) { this.title = title; }
    }

    /** View helper passed to binders. */
    public static final class Row {
        public final ItemRowBinding b;

        public Row(ItemRowBinding b) { this.b = b; }

        public Row icon(@DrawableRes int res, Tone tone) {
            b.rowIconTile.setVisibility(View.VISIBLE);
            b.rowAvatar.setVisibility(View.GONE);
            b.rowIcon.setVisibility(View.VISIBLE);
            b.rowIcon.setImageResource(res);
            tone.tile(b.rowIconTile, b.rowIcon);
            return this;
        }

        public Row avatar(String name) {
            b.rowIconTile.setVisibility(View.VISIBLE);
            b.rowIcon.setVisibility(View.GONE);
            b.rowAvatar.setVisibility(View.VISIBLE);
            b.rowAvatar.setText(Texts.initials(name));
            Tone.GOLD.tile(b.rowIconTile, null);
            return this;
        }

        public Row noIcon() {
            b.rowIconTile.setVisibility(View.GONE);
            return this;
        }

        public Row title(@Nullable CharSequence s) { b.rowTitle.setText(s); return this; }
        public Row subtitle(@Nullable CharSequence s) { Ui.text(b.rowSubtitle, s); return this; }
        public Row meta(@Nullable CharSequence s) { Ui.text(b.rowMeta, s); return this; }
        public Row trailing(@Nullable CharSequence s) { Ui.text(b.rowTrailing, s); return this; }

        public Row badge(@Nullable CharSequence s, Tone tone) {
            Ui.text(b.rowBadge, s);
            if (!Texts.blank(s)) tone.badge(b.rowBadge);
            return this;
        }

        public Row chevron(boolean show) { Ui.show(b.rowChevron, show); return this; }

        public Row unread(boolean unread) { Ui.show(b.rowDot, unread); return this; }

        public void reset() {
            b.rowIconTile.setVisibility(View.VISIBLE);
            b.rowIcon.setVisibility(View.VISIBLE);
            b.rowAvatar.setVisibility(View.GONE);
            b.rowSubtitle.setVisibility(View.GONE);
            b.rowMeta.setVisibility(View.GONE);
            b.rowTrailing.setVisibility(View.GONE);
            b.rowBadge.setVisibility(View.GONE);
            b.rowDot.setVisibility(View.GONE);
            b.rowChevron.setVisibility(View.VISIBLE);
        }
    }

    private final List<Object> entries = new ArrayList<>();
    private final Binder<T> binder;
    @Nullable private OnClick<T> onClick;
    @Nullable private OnClick<T> onLongClick;
    @Nullable private GroupKey<T> groupKey;

    public RowAdapter(Binder<T> binder) {
        this.binder = binder;
    }

    public RowAdapter<T> onClick(@Nullable OnClick<T> c) { this.onClick = c; return this; }
    public RowAdapter<T> onLongClick(@Nullable OnClick<T> c) { this.onLongClick = c; return this; }
    public RowAdapter<T> groupBy(@Nullable GroupKey<T> g) { this.groupKey = g; return this; }

    @Override
    public void setItems(List<T> items) {
        entries.clear();
        append(items);
        notifyDataSetChanged();
    }

    @Override
    public void addItems(List<T> items) {
        int start = entries.size();
        append(items);
        notifyItemRangeInserted(start, entries.size() - start);
    }

    private void append(@Nullable List<T> items) {
        if (items == null) return;
        String last = lastGroup();
        for (T item : items) {
            if (groupKey != null) {
                String g = groupKey.of(item);
                if (g != null && !g.equals(last)) {
                    entries.add(new Header(g));
                    last = g;
                }
            }
            entries.add(item);
        }
    }

    @Nullable
    private String lastGroup() {
        if (groupKey == null) return null;
        for (int i = entries.size() - 1; i >= 0; i--) {
            if (entries.get(i) instanceof Header) return ((Header) entries.get(i)).title;
        }
        return null;
    }

    public void addHeader(String title) {
        entries.add(new Header(title));
        notifyItemInserted(entries.size() - 1);
    }

    public void clear() {
        entries.clear();
        notifyDataSetChanged();
    }

    @SuppressWarnings("unchecked")
    public List<T> items() {
        List<T> out = new ArrayList<>();
        for (Object o : entries) if (!(o instanceof Header)) out.add((T) o);
        return out;
    }

    @Override
    public boolean isEmpty() {
        for (Object o : entries) if (!(o instanceof Header)) return false;
        return true;
    }

    @Override
    public int getItemViewType(int position) {
        return entries.get(position) instanceof Header ? TYPE_HEADER : TYPE_ROW;
    }

    @Override
    public int getItemCount() {
        return entries.size();
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inf = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_HEADER) {
            return new HeaderHolder(ItemSectionHeaderBinding.inflate(inf, parent, false));
        }
        return new RowHolder(ItemRowBinding.inflate(inf, parent, false));
    }

    @SuppressWarnings("unchecked")
    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Object e = entries.get(position);
        if (holder instanceof HeaderHolder) {
            ((HeaderHolder) holder).b.sectionTitle.setText(((Header) e).title);
            return;
        }
        RowHolder h = (RowHolder) holder;
        T item = (T) e;
        h.row.reset();
        binder.bind(h.row, item);
        h.itemView.setOnClickListener(onClick == null ? null : v -> onClick.onClick(item));
        h.itemView.setClickable(onClick != null);
        if (onLongClick != null) {
            h.itemView.setOnLongClickListener(v -> {
                onLongClick.onClick(item);
                return true;
            });
        } else {
            h.itemView.setOnLongClickListener(null);
        }
    }

    static final class RowHolder extends RecyclerView.ViewHolder {
        final Row row;

        RowHolder(ItemRowBinding b) {
            super(b.getRoot());
            row = new Row(b);
        }
    }

    static final class HeaderHolder extends RecyclerView.ViewHolder {
        final ItemSectionHeaderBinding b;

        HeaderHolder(ItemSectionHeaderBinding b) {
            super(b.getRoot());
            this.b = b;
        }
    }
}
