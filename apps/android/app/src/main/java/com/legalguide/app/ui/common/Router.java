package com.legalguide.app.ui.common;

import android.content.Context;
import android.content.Intent;

import androidx.annotation.Nullable;

import com.legalguide.app.core.util.Intents;
import com.legalguide.app.core.util.Texts;
import com.legalguide.app.data.model.Citation;
import com.legalguide.app.data.model.LegalUpdate;
import com.legalguide.app.ui.ai.ChatActivity;
import com.legalguide.app.ui.billing.InvoicesActivity;
import com.legalguide.app.ui.billing.PaymentStatusActivity;
import com.legalguide.app.ui.billing.PlansActivity;
import com.legalguide.app.ui.billing.WalletActivity;
import com.legalguide.app.ui.cases.AnalysisActivity;
import com.legalguide.app.ui.cases.CaseDetailActivity;
import com.legalguide.app.ui.clients.ClientDetailActivity;
import com.legalguide.app.ui.contracts.ContractViewActivity;
import com.legalguide.app.ui.hearings.HearingFormActivity;
import com.legalguide.app.ui.hearings.HearingsActivity;
import com.legalguide.app.ui.library.SourceViewerActivity;
import com.legalguide.app.ui.notifications.NotificationsActivity;
import com.legalguide.app.ui.support.TicketDetailActivity;

/** Opens any server-referenced entity (notifications, search results, citations, updates). */
public final class Router {
    private Router() {}

    public static void open(Context c, @Nullable String type, @Nullable String id) {
        if (type == null) return;
        boolean hasId = !Texts.blank(id);
        switch (type) {
            case "CASE":
                if (hasId) CaseDetailActivity.start(c, id);
                break;
            case "HEARING":
                if (hasId) HearingFormActivity.edit(c, id);
                else c.startActivity(new Intent(c, HearingsActivity.class));
                break;
            case "CLIENT":
                if (hasId) ClientDetailActivity.start(c, id);
                break;
            case "CONTRACT":
                if (hasId) ContractViewActivity.start(c, id);
                break;
            case "TICKET":
                if (hasId) TicketDetailActivity.start(c, id);
                break;
            case "ORDER":
                if (hasId) PaymentStatusActivity.start(c, id, null);
                break;
            case "INVOICE":
                c.startActivity(new Intent(c, InvoicesActivity.class));
                break;
            case "CHAT":
                if (hasId) ChatActivity.open(c, id);
                break;
            case "ANALYSIS":
                if (hasId) AnalysisActivity.start(c, id);
                break;
            case "PLANS":
                c.startActivity(new Intent(c, PlansActivity.class));
                break;
            case "WALLET":
                c.startActivity(new Intent(c, WalletActivity.class));
                break;
            case "LAW":
            case "ARTICLE":
            case "JUDGMENT":
            case "PRINCIPLE":
            case "BOOK":
            case "UPDATE":
                if (hasId) SourceViewerActivity.start(c, type, id, null);
                break;
            default:
                c.startActivity(new Intent(c, NotificationsActivity.class));
        }
    }

    public static void openCitation(Context c, Citation ct) {
        if (!Texts.blank(ct.sourceType) && !Texts.blank(ct.sourceId)) {
            SourceViewerActivity.start(c, ct.sourceType, ct.sourceId, parsePage(ct.page));
        } else if (!Texts.blank(ct.url)) {
            Intents.openUrl(c, ct.url);
        } else {
            Dialogs.info(c, Texts.orDash(ct.title), Texts.orDash(ct.excerpt));
        }
    }

    public static void openUpdate(Context c, LegalUpdate u) {
        if (!Texts.blank(u.refType) && !Texts.blank(u.refId)) open(c, u.refType, u.refId);
        else SourceViewerActivity.start(c, "UPDATE", u.id, null);
    }

    @Nullable
    private static Integer parsePage(@Nullable String page) {
        if (page == null) return null;
        try {
            return Integer.parseInt(Texts.asciiDigits(page).replaceAll("[^0-9]", ""));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
