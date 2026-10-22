package com.legalguide.app.data.model;


/** Legal source cited by the AI. verified=false means the server could not validate it. */
public class Citation {
    public String id;
    public String sourceType;
    public String sourceId;
    public String title;
    public String law;
    public String article;
    public String page;
    public String judgment;
    public String url;
    public boolean verified;
    public String excerpt;
}
