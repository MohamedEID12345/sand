package com.legalguide.app.data.model;


/** A payment method enabled on the merchant account. type: REDIRECT | REFERENCE | MANUAL. */
public class PaymentMethod {
    public String code;
    public String label;
    public String description;
    public String provider;
    public String type;
    public String icon;
}
