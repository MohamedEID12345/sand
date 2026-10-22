package com.legalguide.app.ui.billing;

import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.Nullable;

import com.legalguide.app.R;
import com.legalguide.app.core.net.ApiClient;
import com.legalguide.app.core.session.Session;
import com.legalguide.app.core.util.Dates;
import com.legalguide.app.core.util.Money;
import com.legalguide.app.core.util.Texts;
import com.legalguide.app.data.model.AiCost;
import com.legalguide.app.data.model.CreditTransaction;
import com.legalguide.app.data.model.Page;
import com.legalguide.app.data.model.Wallet;
import com.legalguide.app.databinding.ViewWalletHeaderBinding;
import com.legalguide.app.ui.common.ListController;
import com.legalguide.app.ui.common.RowAdapter;
import com.legalguide.app.ui.common.SimpleListActivity;
import com.legalguide.app.ui.common.Tone;
import com.legalguide.app.ui.common.Ui;

import java.util.List;

import retrofit2.Call;

/** Credit wallet: balance, free uses, operation costs and the ledger. */
public class WalletActivity extends SimpleListActivity<CreditTransaction> {
    private ViewWalletHeaderBinding h;

    @Override
    protected CharSequence title() {
        return getString(R.string.wallet_title);
    }

    @Nullable
    @Override
    protected View createHeader(ViewGroup parent) {
        h = ViewWalletHeaderBinding.inflate(LayoutInflater.from(this), parent, false);
        h.topup.setOnClickListener(v -> startActivity(new Intent(this, PlansActivity.class)
                .putExtra(PlansActivity.EXTRA_TAB, PlansActivity.TAB_CREDITS)));
        return h.getRoot();
    }

    @Override
    protected void onReady() {
        Session.get().bootstrap().observe(this, boot -> bindWallet(boot != null ? boot.wallet : null));
        bindCosts(Session.get().aiCosts());
        call(ApiClient.api().aiCosts(), p -> {
            if (p != null) {
                Session.get().setAiCosts(p.items);
                bindCosts(p.items);
            }
        }, e -> { });
    }

    @Override
    protected void configure(ListController<CreditTransaction> c) {
        c.empty(R.drawable.ic_history, getString(R.string.wallet_no_transactions), null);
        c.onLoaded((items, first) -> {
            if (first) call(ApiClient.api().wallet(), w -> {
                Session.get().updateWallet(w);
                bindWallet(w);
            }, e -> { });
        });
    }

    private void bindWallet(@Nullable Wallet w) {
        if (w == null || h == null) return;
        h.remaining.setText(Money.number(w.remainingCredits));
        h.detail.setText(getString(R.string.wallet_detail, Money.number(w.totalCredits), Money.number(w.usedCredits)));
        h.freeUses.setText(Money.number(w.freeServiceUses));
        h.bonus.setText(Money.number(w.bonusCredits));
    }

    private void bindCosts(@Nullable List<AiCost> costs) {
        if (h == null) return;
        h.costs.removeAllViews();
        boolean any = costs != null && !costs.isEmpty();
        Ui.show(h.costsCard, any);
        if (!any) return;
        for (AiCost c : costs) {
            Ui.kv(h.costs, Texts.orDash(c.label), c.credits > 0 ? getString(R.string.credits_count, String.valueOf(c.credits))
                    : getString(R.string.free));
        }
    }

    @Override
    protected Call<Page<CreditTransaction>> load(@Nullable String cursor) {
        return ApiClient.api().transactions(cursor);
    }

    @Override
    protected void bind(RowAdapter.Row row, CreditTransaction t) {
        boolean plus = t.amount > 0;
        row.icon(plus ? R.drawable.ic_add : R.drawable.ic_ai, plus ? Tone.SUCCESS : Tone.AI)
                .title(Texts.blank(t.description) ? typeLabel(t.type) : t.description)
                .meta(Texts.joinNonBlank(" • ", typeLabel(t.type), Dates.dayTime(t.createdAt)))
                .trailing(Money.signed(t.amount))
                .chevron(false);
        row.b.rowTrailing.setTextColor(getColor(plus ? R.color.tone_success : R.color.tone_danger));
    }

    private String typeLabel(@Nullable String type) {
        if (type == null) return "";
        switch (type) {
            case "FREE_GRANT": return getString(R.string.tx_free_grant);
            case "PURCHASE": return getString(R.string.tx_purchase);
            case "AI_USAGE": return getString(R.string.tx_ai_usage);
            case "BONUS": return getString(R.string.tx_bonus);
            case "ADMIN_ADJUSTMENT": return getString(R.string.tx_admin);
            case "REFUND": return getString(R.string.tx_refund);
            case "EXPIRATION": return getString(R.string.tx_expiration);
            default: return type;
        }
    }
}
