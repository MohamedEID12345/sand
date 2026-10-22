package com.legalguide.app.ui.billing;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.tabs.TabLayout;
import com.legalguide.app.R;
import com.legalguide.app.core.net.ApiClient;
import com.legalguide.app.core.session.Session;
import com.legalguide.app.core.util.Dates;
import com.legalguide.app.core.util.Money;
import com.legalguide.app.core.util.Texts;
import com.legalguide.app.data.model.CreditPack;
import com.legalguide.app.data.model.Plan;
import com.legalguide.app.data.model.PlanLimits;
import com.legalguide.app.data.model.Subscription;
import com.legalguide.app.databinding.ActivityPlansBinding;
import com.legalguide.app.databinding.ItemCreditPackBinding;
import com.legalguide.app.databinding.ItemPlanBinding;
import com.legalguide.app.ui.common.BaseActivity;
import com.legalguide.app.ui.common.Tone;
import com.legalguide.app.ui.common.Ui;

import java.util.ArrayList;
import java.util.List;

/** Plans and AI credit packs — prices, credits and features all come from the admin panel. */
public class PlansActivity extends BaseActivity {
    public static final String EXTRA_TAB = "tab";
    public static final int TAB_PLANS = 0, TAB_CREDITS = 1;

    private ActivityPlansBinding b;
    private final List<Plan> plans = new ArrayList<>();
    private final List<CreditPack> packs = new ArrayList<>();
    private int tab;
    private boolean plansLoaded, packsLoaded;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!ensureSession()) return;
        b = ActivityPlansBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        applyInsets(b.getRoot());
        setupToolbar(b.toolbar, R.string.plans_title, true);
        tab = getIntent().getIntExtra(EXTRA_TAB, TAB_PLANS);

        b.tabs.addTab(b.tabs.newTab().setText(R.string.plans_tab_plans));
        b.tabs.addTab(b.tabs.newTab().setText(R.string.plans_tab_credits));
        TabLayout.Tab t = b.tabs.getTabAt(tab);
        if (t != null) t.select();
        b.tabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override public void onTabSelected(TabLayout.Tab tb) { tab = tb.getPosition(); render(); }
            @Override public void onTabUnselected(TabLayout.Tab tb) {}
            @Override public void onTabReselected(TabLayout.Tab tb) {}
        });
        b.list.setLayoutManager(new LinearLayoutManager(this));
        b.list.setAdapter(adapter);
        bindSubscription(Session.get().current() != null ? Session.get().current().subscription : null);
        call(ApiClient.api().subscription(), this::bindSubscription, e -> { });
        load();
    }

    private void load() {
        b.state.loading();
        call(ApiClient.api().plans(), p -> {
            plans.clear();
            if (p != null && p.items != null) plans.addAll(p.items);
            plansLoaded = true;
            render();
        }, e -> b.state.error(e, this::load));
        call(ApiClient.api().creditPacks(), p -> {
            packs.clear();
            if (p != null && p.items != null) packs.addAll(p.items);
            packsLoaded = true;
            render();
        }, e -> { packsLoaded = true; render(); });
    }

    private void bindSubscription(@Nullable Subscription s) {
        boolean active = s != null && s.isActive();
        b.currentPlan.setText(active && !Texts.blank(s.planName) ? s.planName : getString(R.string.plan_free));
        if (active) {
            b.currentPlanInfo.setText(Texts.joinNonBlank(" • ",
                    s.endAt != null ? getString(R.string.plan_ends_at, Dates.day(s.endAt)) : null,
                    s.daysLeft != null ? getString(R.string.home_sub_days_left, s.daysLeft) : null));
        } else {
            b.currentPlanInfo.setText(R.string.plan_free_info);
        }
    }

    private void render() {
        boolean ready = tab == TAB_PLANS ? plansLoaded : packsLoaded;
        if (!ready) return;
        boolean empty = tab == TAB_PLANS ? plans.isEmpty() : packs.isEmpty();
        if (empty) b.state.empty(R.drawable.ic_crown, getString(R.string.plans_empty), null);
        else b.state.hide();
        adapter.notifyDataSetChanged();
        b.list.scheduleLayoutAnimation();
    }

    private final RecyclerView.Adapter<RecyclerView.ViewHolder> adapter = new RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        @Override
        public int getItemViewType(int position) {
            return tab;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LayoutInflater inf = LayoutInflater.from(parent.getContext());
            if (viewType == TAB_PLANS) {
                ItemPlanBinding pb = ItemPlanBinding.inflate(inf, parent, false);
                pb.getRoot().setTag(pb);
                return new RecyclerView.ViewHolder(pb.getRoot()) {};
            }
            ItemCreditPackBinding cb = ItemCreditPackBinding.inflate(inf, parent, false);
            cb.getRoot().setTag(cb);
            return new RecyclerView.ViewHolder(cb.getRoot()) {};
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder h, int position) {
            Object tag = h.itemView.getTag();
            if (tag instanceof ItemPlanBinding) bindPlan((ItemPlanBinding) tag, plans.get(position));
            else bindPack((ItemCreditPackBinding) tag, packs.get(position));
        }

        @Override
        public int getItemCount() {
            return tab == TAB_PLANS ? plans.size() : packs.size();
        }
    };

    private void bindPlan(ItemPlanBinding pb, Plan p) {
        pb.getRoot().setStrokeColor(getColor(p.highlighted ? R.color.brand_gold : R.color.lg_outline_variant));
        pb.getRoot().setStrokeWidth(Ui.dp(this, p.highlighted ? 2 : 1));
        Ui.text(pb.planBadge, p.current ? getString(R.string.plan_current) : p.badge);
        if (pb.planBadge.getVisibility() == View.VISIBLE) (p.current ? Tone.SUCCESS : Tone.GOLD).badge(pb.planBadge);
        pb.planName.setText(p.name);
        Ui.text(pb.planDescription, p.description);
        pb.planPrice.setText(p.isFree || p.price <= 0 ? getString(R.string.plan_free_price) : Money.format(p.price, p.currency));
        pb.planDuration.setText(p.durationDays > 0 ? getString(R.string.plan_per_days, p.durationDays) : "");
        pb.planFeatures.removeAllViews();
        if (p.credits > 0) feature(pb.planFeatures, getString(R.string.plan_feature_credits, p.credits));
        if (p.freeServiceUses > 0) feature(pb.planFeatures, getString(R.string.plan_feature_free_uses, p.freeServiceUses));
        if (p.features != null) for (String f : p.features) feature(pb.planFeatures, f);
        PlanLimits l = p.limits;
        if (l != null) {
            feature(pb.planFeatures, getString(R.string.plan_limit_cases, limit(l.cases)));
            if (l.teamMembers != null) feature(pb.planFeatures, getString(R.string.plan_limit_team, limit(l.teamMembers)));
            if (l.storageMb != null) feature(pb.planFeatures, getString(R.string.plan_limit_storage, limit(l.storageMb)));
        }
        pb.planBuy.setEnabled(!p.current && !p.isFree);
        pb.planBuy.setText(p.current ? R.string.plan_current : R.string.plan_subscribe);
        pb.planBuy.setOnClickListener(v -> CheckoutActivity.start(this, "PLAN", p.id, p.name,
                getString(R.string.plan_per_days, p.durationDays), p.price, p.currency));
    }

    private String limit(@Nullable Integer v) {
        return v == null ? getString(R.string.unlimited) : Money.number(v);
    }

    private void feature(ViewGroup box, String text) {
        TextView tv = (TextView) LayoutInflater.from(this).inflate(R.layout.item_plan_feature, box, false);
        tv.setText(text);
        box.addView(tv);
    }

    private void bindPack(ItemCreditPackBinding cb, CreditPack p) {
        cb.packCredits.setText(Money.number(p.credits));
        Ui.text(cb.packBonus, p.bonusCredits > 0 ? getString(R.string.pack_bonus, p.bonusCredits) : null);
        cb.packName.setText(p.name);
        Ui.text(cb.packBadge, p.badge);
        if (!Texts.blank(p.badge)) Tone.GOLD.badge(cb.packBadge);
        cb.packPrice.setText(Money.format(p.price, p.currency));
        cb.packBuy.setOnClickListener(v -> CheckoutActivity.start(this, "CREDIT_PACK", p.id, p.name,
                getString(R.string.pack_subtitle, p.credits + p.bonusCredits), p.price, p.currency));
    }
}
