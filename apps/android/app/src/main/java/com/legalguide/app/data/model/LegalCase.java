package com.legalguide.app.data.model;

import java.util.List;
import java.util.Map;

/** Case (tenant-scoped on the server). */
public class LegalCase {
    public String id;
    public String caseTypeId;
    public String caseTypeName;
    public String caseNumber;
    public Integer year;
    public String court;
    public String circuit;
    public String clientId;
    public String clientName;
    public String opponent;
    public List<String> lawyers;
    public String status;
    public String statusLabel;
    public String priority;
    public String priorityLabel;
    public String description;
    public Map<String, Object> customFields;
    public String nextHearingAt;
    public int documentsCount;
    public int hearingsCount;
    public String createdAt;
    public String updatedAt;

    public String displayTitle() {
        StringBuilder sb = new StringBuilder();
        if (caseNumber != null) sb.append(caseNumber);
        if (year != null) sb.append(sb.length() > 0 ? " / " : "").append(year);
        return sb.length() > 0 ? sb.toString() : (caseTypeName != null ? caseTypeName : "");
    }

    @Override public String toString() { return displayTitle() + (clientName != null ? " — " + clientName : ""); }
}
