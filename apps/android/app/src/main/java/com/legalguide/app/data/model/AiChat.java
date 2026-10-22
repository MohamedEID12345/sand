package com.legalguide.app.data.model;


/** AI conversation. mode: QUESTION | RESEARCH | DEEP_RESEARCH | MEMO | DEBATE | COMPARE | DOCUMENT. */
public class AiChat {
    public String id;
    public String title;
    public String mode;
    public String caseId;
    public String caseTitle;
    public String lastMessage;
    public String updatedAt;
}
