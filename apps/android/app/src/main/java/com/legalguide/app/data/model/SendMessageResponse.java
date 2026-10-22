package com.legalguide.app.data.model;


/** POST /ai/chats/{id}/messages response. */
public class SendMessageResponse {
    public AiMessage userMessage;
    public AiMessage message;
    public Wallet wallet;
    public Charge charge;
    public AiChat chat;
}
