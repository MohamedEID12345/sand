package com.legalguide.app.data.model;

import java.util.List;

/** Law office (tenant). */
public class Office {
    public String id;
    public String name;
    public int seatsUsed;
    public Integer seatsMax;
    public List<OfficeMember> members;
    public List<Option> roles;
}
