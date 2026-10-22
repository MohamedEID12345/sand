package com.legalguide.app.data.model;


/** Order. status: PENDING | PAID | FAILED | CANCELLED | EXPIRED | REFUNDED | PENDING_VERIFICATION. */
public class Order {
    public String id;
    public String itemType;
    public String itemId;
    public String itemName;
    public String status;
    public double amount;
    public double discount;
    public double total;
    public String currency;
    public String paymentMethod;
    public String provider;
    public String referenceNumber;
    public String expiresAt;
    public String createdAt;
    public String paidAt;
    public String failureReason;
    public int creditsGranted;
}
