package com.legalguide.app.data.model;


/** Current subscription snapshot. */
public class Subscription {
    public String id;
    public String planId;
    public String planName;
    public String status;
    public String startAt;
    public String endAt;
    public Integer daysLeft;

    public boolean isActive() { return "ACTIVE".equals(status); }
}
