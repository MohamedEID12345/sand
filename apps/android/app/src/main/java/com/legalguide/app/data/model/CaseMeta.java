package com.legalguide.app.data.model;

import java.util.List;

/** Case lookups (all admin-managed): GET /cases/meta. */
public class CaseMeta {
    public List<Option> statuses;
    public List<Option> priorities;
    public List<CaseType> caseTypes;
    public List<Option> documentTypes;
    public List<Option> deadlineEvents;
    public List<AnalysisType> analysisTypes;
    public List<Option> hearingStatuses;
}
