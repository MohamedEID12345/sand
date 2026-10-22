package com.legalguide.app.data.model;

import java.util.List;

/** Subscription plan (fully managed from the admin panel). */
public class Plan {
    public String id;
    public String name;
    public String description;
    public double price;
    public String currency;
    public int durationDays;
    public int credits;
    public int freeServiceUses;
    public List<String> features;
    public PlanLimits limits;
    public boolean highlighted;
    public String badge;
    public boolean isFree;
    public boolean current;
}
