package com.legalguide.app.ui.common;

import java.util.List;

/** Minimal contract used by {@link ListController} to feed any adapter. */
public interface PagedAdapter<T> {
    void setItems(List<T> items);
    void addItems(List<T> items);
    boolean isEmpty();
}
