package com.legalguide.app.data.model;

import java.util.List;

/** Uniform view of any legal source: GET /library/source?type=&id=&page=. */
public class SourceView {
    public String type;
    public String id;
    public String title;
    public String subtitle;
    public String body;
    public List<KeyValue> meta;
    public List<LibraryItem> sections;
    public List<LibraryItem> related;
    public Boolean verified;
    public String status;
    public String url;
    public Integer page;
    public Integer pageCount;
}
