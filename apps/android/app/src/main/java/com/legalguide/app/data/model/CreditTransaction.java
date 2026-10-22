package com.legalguide.app.data.model;


/** Wallet ledger entry. type: FREE_GRANT | PURCHASE | AI_USAGE | BONUS | ADMIN_ADJUSTMENT | REFUND | EXPIRATION. */
public class CreditTransaction {
    public String id;
    public int amount;
    public String type;
    public String operation;
    public String description;
    public String createdAt;
}
