package com.legalguide.app.data.model;


/** Uploaded case document. ocrStatus: PENDING | PROCESSING | DONE | FAILED | NOT_REQUIRED. */
public class CaseDocument {
    public String id;
    public String caseId;
    public String title;
    public String docType;
    public String docTypeLabel;
    public String fileName;
    public String mimeType;
    public long sizeBytes;
    public String ocrStatus;
    public Integer pages;
    public String createdAt;
    public String uploadedBy;
}
