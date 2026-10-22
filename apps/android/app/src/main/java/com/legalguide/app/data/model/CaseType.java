package com.legalguide.app.data.model;

import java.util.List;

/** Admin-defined case type with its custom fields. */
public class CaseType {
    public String id;
    public String name;
    public List<FormField> fields;

    @Override public String toString() { return name; }
}
