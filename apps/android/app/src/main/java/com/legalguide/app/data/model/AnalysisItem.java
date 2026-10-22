package com.legalguide.app.data.model;

import java.util.List;

/** Single finding. severity: HIGH | MEDIUM | LOW | INFO. */
public class AnalysisItem {
    public String title;
    public String body;
    public String severity;
    public List<KeyValue> fields;
    public List<String> refs;
}
