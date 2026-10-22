package com.legalguide.app.data.model;


/** Result of validating a promo code. */
public class PromoResult {
    public boolean valid;
    public String code;
    public String message;
    public String discountType;
    public double discountValue;
    public int bonusCredits;
    public double originalAmount;
    public double discountAmount;
    public double finalAmount;
    public String currency;
}
