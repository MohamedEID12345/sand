package com.legalguide.app.data.model;

import java.util.List;

/** Dynamic form field (contract templates, case types). type: text | textarea | number | money | date | select | multiselect | checkbox | phone | email | national_id. */
public class FormField {
    public String key;
    public String label;
    public String type;
    public boolean required;
    public List<Option> options;
    public String placeholder;
    public String hint;
    public Double min;
    public Double max;
    public Integer maxLength;
    public String defaultValue;
    public String group;
}
