package com.legalguide.app.data.model;


/** Provider instructions returned when an order is created. */
public class PaymentInfo {
    public String provider;
    public String type;
    public String checkoutUrl;
    public String referenceNumber;
    public String expiresAt;
    public String instructions;
    public BankDetails bankDetails;
}
