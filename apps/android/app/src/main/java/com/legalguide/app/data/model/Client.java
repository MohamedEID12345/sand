package com.legalguide.app.data.model;

import java.util.List;

/** Client of the office. */
public class Client {
    public String id;
    public String name;
    public String phone;
    public String email;
    public String address;
    public String notes;
    public Double feesTotal;
    public Double feesPaid;
    public int casesCount;
    public List<LegalCase> cases;
    public String createdAt;

    @Override public String toString() { return name; }
}
