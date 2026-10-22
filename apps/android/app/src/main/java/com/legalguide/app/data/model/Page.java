package com.legalguide.app.data.model;

import java.util.List;

/** Cursor-paginated list envelope. */
public class Page<T> {
    public List<T> items;
    public String nextCursor;
    public Integer total;
}
