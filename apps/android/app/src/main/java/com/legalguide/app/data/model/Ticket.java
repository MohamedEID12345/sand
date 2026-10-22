package com.legalguide.app.data.model;

import java.util.List;

/** Support ticket. */
public class Ticket {
    public String id;
    public String subject;
    public String category;
    public String priority;
    public String status;
    public String statusLabel;
    public String createdAt;
    public String updatedAt;
    public String lastMessage;
    public List<TicketMessage> messages;
}
