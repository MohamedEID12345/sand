package com.legalguide.app.ui.notifications;

import androidx.annotation.Nullable;

import com.legalguide.app.R;
import com.legalguide.app.core.net.ApiClient;
import com.legalguide.app.core.session.Session;
import com.legalguide.app.core.util.Dates;
import com.legalguide.app.data.model.AppNotification;
import com.legalguide.app.data.model.Page;
import com.legalguide.app.ui.common.ListController;
import com.legalguide.app.ui.common.RowAdapter;
import com.legalguide.app.ui.common.Router;
import com.legalguide.app.ui.common.SimpleListActivity;
import com.legalguide.app.ui.common.Tone;

import retrofit2.Call;

public class NotificationsActivity extends SimpleListActivity<AppNotification> {

    @Override
    protected CharSequence title() {
        return getString(R.string.notifications_title);
    }

    @Override
    protected Call<Page<AppNotification>> load(@Nullable String cursor) {
        return ApiClient.api().notifications(cursor, null);
    }

    @Override
    protected void bind(RowAdapter.Row row, AppNotification n) {
        row.icon(icon(n.type), tone(n.type)).title(n.title).subtitle(n.body)
                .meta(Dates.relative(n.createdAt)).unread(!n.read).chevron(n.refType != null);
        row.b.rowSubtitle.setMaxLines(4);
    }

    private static int icon(@Nullable String t) {
        if (t == null) return R.drawable.ic_bell;
        switch (t) {
            case "HEARING": return R.drawable.ic_calendar;
            case "DEADLINE": return R.drawable.ic_timer;
            case "PAYMENT_SUCCESS": return R.drawable.ic_check_circle;
            case "PAYMENT_FAILED": return R.drawable.ic_error;
            case "SUBSCRIPTION_EXPIRING": return R.drawable.ic_crown;
            case "CREDITS_LOW": return R.drawable.ic_wallet;
            case "LEGAL_UPDATE": return R.drawable.ic_campaign;
            case "TICKET_REPLY": return R.drawable.ic_headset;
            case "ANALYSIS_DONE": return R.drawable.ic_insights;
            default: return R.drawable.ic_bell;
        }
    }

    private static Tone tone(@Nullable String t) {
        if (t == null) return Tone.INFO;
        switch (t) {
            case "HEARING": case "DEADLINE": case "PAYMENT_FAILED": return Tone.DANGER;
            case "PAYMENT_SUCCESS": return Tone.SUCCESS;
            case "SUBSCRIPTION_EXPIRING": case "CREDITS_LOW": case "LEGAL_UPDATE": return Tone.GOLD;
            case "ANALYSIS_DONE": return Tone.AI;
            default: return Tone.INFO;
        }
    }

    @Override
    protected void onItemClick(AppNotification n) {
        if (!n.read) {
            n.read = true;
            adapter.notifyDataSetChanged();
            call(ApiClient.api().markRead(n.id), ok -> refreshUnread(), e -> { });
        }
        if (n.refType != null) Router.open(this, n.refType, n.refId);
    }

    @Override
    protected void configure(ListController<AppNotification> c) {
        c.empty(R.drawable.ic_bell, getString(R.string.notifications_empty), null);
    }

    @Override
    protected void onReady() {
        b.toolbar.inflateMenu(R.menu.menu_notifications);
        b.toolbar.setOnMenuItemClickListener(item -> {
            call(ApiClient.api().markAllRead(), ok -> {
                Session.get().setUnread(0);
                reload();
            });
            return true;
        });
    }

    private void refreshUnread() {
        call(ApiClient.api().unreadCount(), c -> {
            if (c != null) Session.get().setUnread(c.count);
        }, e -> { });
    }
}
