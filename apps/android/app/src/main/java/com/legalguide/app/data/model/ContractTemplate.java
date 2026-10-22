package com.legalguide.app.data.model;

import java.util.List;

/** Contract template managed by admin. */
public class ContractTemplate {
    public String id;
    public String name;
    public String category;
    public String categoryLabel;
    public String description;
    public int credits;
    public List<FormField> fields;
}
