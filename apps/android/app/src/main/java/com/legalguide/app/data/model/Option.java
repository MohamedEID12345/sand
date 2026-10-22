package com.legalguide.app.data.model;


/** Generic value/label pair used by dynamic lists. */
public class Option {
    public String value;
    public String label;

    public Option() {}
    public Option(String value, String label) { this.value = value; this.label = label; }
    @Override public String toString() { return label != null ? label : value; }
}
