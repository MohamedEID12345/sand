package com.legalguide.app.data.model;

import java.util.List;

/** Generated contract draft. */
public class Contract {
    public String id;
    public String title;
    public String templateId;
    public String templateName;
    public String body;
    public String status;
    public List<String> reviewNotes;
    public String caseId;
    public String caseTitle;
    public String createdAt;
    public List<Citation> citations;
}
