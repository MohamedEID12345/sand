package com.legalguide.app.ui.common;

import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

/** Wraps a single pre-built view so it can sit above a list inside a ConcatAdapter. */
public class HeaderAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private final View view;
    private boolean visible = true;

    public HeaderAdapter(View view) {
        this.view = view;
    }

    public void setVisible(boolean v) {
        if (v == visible) return;
        visible = v;
        if (v) notifyItemInserted(0);
        else notifyItemRemoved(0);
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (view.getParent() instanceof ViewGroup) ((ViewGroup) view.getParent()).removeView(view);
        view.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return new RecyclerView.ViewHolder(view) {};
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
    }

    @Override
    public int getItemCount() {
        return visible ? 1 : 0;
    }
}
