package com.legalguide.app.data.model;


/** Generic searchable item (library, global search). type: BOOK | LAW | ARTICLE | JUDGMENT | PRINCIPLE | UPDATE | CASE | CLIENT | DOCUMENT | CONTRACT | NOTE | HEARING. */
public class LibraryItem {
    public String id;
    public String type;
    public String title;
    public String subtitle;
    public String snippet;
    public String categoryName;
    public String effectiveDate;
    public String status;
}
