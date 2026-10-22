package com.legalguide.app.data.model;

import java.util.List;

/** AI case analysis. status: QUEUED | RUNNING | DONE | FAILED. */
public class Analysis {
    public String id;
    public String caseId;
    public String type;
    public String typeLabel;
    public String status;
    public String createdAt;
    public int creditsCharged;
    public String summary;
    public List<AnalysisSection> sections;
    public List<Citation> citations;
    public String disclaimer;
    public String error;

    public boolean isRunning() { return "QUEUED".equals(status) || "RUNNING".equals(status); }
}
