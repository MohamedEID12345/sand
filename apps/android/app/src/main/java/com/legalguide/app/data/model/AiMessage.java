package com.legalguide.app.data.model;

import java.util.List;

/** Chat message. role: user | assistant. */
public class AiMessage {
    public String id;
    public String role;
    public String content;
    public List<Citation> citations;
    public List<Attachment> attachments;
    public String createdAt;
    public int creditsCharged;
    public boolean usedFreeUse;

    /** Local-only UI state (not serialized). */
    public transient int localState;
    public static final int STATE_SENT = 0, STATE_PENDING = 1, STATE_FAILED = 2;

    public boolean isUser() { return "user".equals(role); }
}
