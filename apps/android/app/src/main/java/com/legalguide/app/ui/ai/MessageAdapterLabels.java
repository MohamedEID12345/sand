package com.legalguide.app.ui.ai;

import com.legalguide.app.data.model.Citation;

/** Public access to the citation chip label format. */
public final class MessageAdapterLabels {
    private MessageAdapterLabels() {}

    public static String label(Citation c) {
        return MessageAdapter.citationLabel(c);
    }
}
