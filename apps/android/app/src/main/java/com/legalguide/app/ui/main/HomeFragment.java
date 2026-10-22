package com.legalguide.app.ui.main;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.legalguide.app.R;
import com.legalguide.app.core.net.ApiClient;
import com.legalguide.app.core.notify.ReminderScheduler;
import com.legalguide.app.core.session.Session;
import com.legalguide.app.core.util.Dates;
import com.legalguide.app.core.util.Money;
import com.legalguide.app.core.util.Texts;
import com.legalguide.app.data.model.Bootstrap;
import com.legalguide.app.data.model.Hearing;
import com.legalguide.app.data.model.LegalUpdate;
import com.legalguide.app.databinding.FragmentHomeBinding;
import com.legalguide.app.databinding.ItemQuickActionBinding;
import com.legalguide.app.databinding.ItemRowBinding;
import com.legalguide.app.databinding.ItemStatBinding;
import com.legalguide.app.ui.ai.ChatActivity;
import com.legalguide.app.ui.billing.PlansActivity;
import com.legalguide.app.ui.cases.CaseFormActivity;
import com.legalguide.app.ui.clients.ClientsActivity;
import com.legalguide.app.ui.common.BaseFragment;
import com.legalguide.app.ui.common.RowAdapter;
import com.legalguide.app.ui.common.Router;
import com.legalguide.app.ui.common.Tone;
import com.legalguide.app.ui.common.Ui;
import com.legalguide.app.ui.contracts.ContractTemplatesActivity;
import com.legalguide.app.ui.hearings.HearingsActivity;
import com.legalguide.app.ui.library.LegalUpdatesActivity;
import com.legalguide.app.ui.notifications.NotificationsActivity;
import com.legalguide.app.ui.profile.ProfileActivity;
import com.legalguide.app.ui.search.SearchActivity;

import java.time.LocalTime;
import java.util.List;

public class HomeFragment extends BaseFragment {
    private FragmentHomeBinding b;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        b = FragmentHomeBinding.inflate(inflater, container, false);
        return b.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        b.refresh.setColorSchemeResources(R.color.lg_primary, R.color.brand_gold);
        b.refresh.setOnRefreshListener(() -> {
            if (getActivity() instanceof MainActivity) ((MainActivity) getActivity()).refreshBootstrap(true);
            loadData();
        });
        b.greeting.setText(LocalTime.now().getHour() < 12 ? R.string.home_greeting_morning : R.string.home_greeting_evening);
        b.avatar.setOnClickListener(v -> startActivity(new Intent(requireContext(), ProfileActivity.class)));
        b.search.setOnClickListener(v -> startActivity(new Intent(requireContext(), SearchActivity.class)));
        b.bell.setOnClickListener(v -> startActivity(new Intent(requireContext(), NotificationsActivity.class)));
        b.topup.setOnClickListener(v -> openPlans(PlansActivity.TAB_CREDITS));
        b.plans.setOnClickListener(v -> openPlans(PlansActivity.TAB_PLANS));
        b.hero.setOnClickListener(v -> startActivity(new Intent(requireContext(), com.legalguide.app.ui.billing.WalletActivity.class)));

        action(b.qaAsk, R.drawable.ic_ai, Tone.AI, R.string.mode_question, v -> ChatActivity.start(requireContext(), "QUESTION", null));
        action(b.qaResearch, R.drawable.ic_search, Tone.INFO, R.string.mode_deep_research, v -> ChatActivity.start(requireContext(), "DEEP_RESEARCH", null));
        action(b.qaMemo, R.drawable.ic_notes, Tone.GOLD, R.string.mode_memo, v -> ChatActivity.start(requireContext(), "MEMO", null));
        action(b.qaCase, R.drawable.ic_gavel, Tone.PRIMARY, R.string.home_new_case, v -> startActivity(new Intent(requireContext(), CaseFormActivity.class)));
        action(b.qaContract, R.drawable.ic_contract, Tone.SUCCESS, R.string.home_new_contract, v -> startActivity(new Intent(requireContext(), ContractTemplatesActivity.class)));
        action(b.qaHearings, R.drawable.ic_calendar, Tone.WARNING, R.string.hearings_title, v -> startActivity(new Intent(requireContext(), HearingsActivity.class)));

        stat(b.statCases, R.string.home_stat_cases, v -> {
            if (getActivity() instanceof MainActivity) ((MainActivity) getActivity()).selectTab(R.id.nav_cases);
        });
        stat(b.statHearings, R.string.home_stat_hearings, v -> startActivity(new Intent(requireContext(), HearingsActivity.class)));
        stat(b.statClients, R.string.home_stat_clients, v -> startActivity(new Intent(requireContext(), ClientsActivity.class)));
        b.allHearings.setOnClickListener(v -> startActivity(new Intent(requireContext(), HearingsActivity.class)));
        b.allUpdates.setOnClickListener(v -> startActivity(new Intent(requireContext(), LegalUpdatesActivity.class)));

        Session.get().bootstrap().observe(getViewLifecycleOwner(), this::bindBootstrap);
        Session.get().unreadCount().observe(getViewLifecycleOwner(), n -> {
            int c = n == null ? 0 : n;
            b.bellBadge.setVisibility(c > 0 ? View.VISIBLE : View.GONE);
            b.bellBadge.setText(c > 99 ? "99+" : String.valueOf(c));
        });
        if (Session.get().config() != null && !Texts.blank(Session.get().config().aiDisclaimer)) {
            b.disclaimer.setText(Session.get().config().aiDisclaimer);
        }
        loadData();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        b = null;
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (!hidden && b != null) loadData();
    }

    @Override
    public void reload() {
        if (b != null) loadData();
    }

    private void openPlans(int tab) {
        startActivity(new Intent(requireContext(), PlansActivity.class).putExtra(PlansActivity.EXTRA_TAB, tab));
    }

    private void action(ItemQuickActionBinding qa, int icon, Tone tone, int label, View.OnClickListener l) {
        qa.qaIcon.setImageResource(icon);
        tone.tile(qa.qaTile, qa.qaIcon);
        qa.qaLabel.setText(label);
        qa.getRoot().setOnClickListener(l);
    }

    private void stat(ItemStatBinding s, int label, View.OnClickListener l) {
        s.statLabel.setText(label);
        s.getRoot().setOnClickListener(l);
    }

    private void bindBootstrap(@Nullable Bootstrap boot) {
        if (b == null || boot == null) return;
        if (boot.user != null) {
            b.userName.setText(boot.user.name);
            b.avatar.setText(Texts.initials(boot.user.name));
        }
        if (boot.wallet != null) {
            b.credits.setText(Money.number(boot.wallet.remainingCredits));
        }
        String plan = boot.subscription != null && boot.subscription.isActive() && !Texts.blank(boot.subscription.planName)
                ? boot.subscription.planName : getString(R.string.plan_free);
        b.planChip.setText(plan);
        StringBuilder detail = new StringBuilder();
        if (boot.wallet != null && boot.wallet.freeServiceUses > 0) {
            detail.append(getString(R.string.home_free_uses_left, boot.wallet.freeServiceUses));
        }
        if (boot.subscription != null && boot.subscription.isActive() && boot.subscription.daysLeft != null) {
            if (detail.length() > 0) detail.append(" • ");
            detail.append(getString(R.string.home_sub_days_left, boot.subscription.daysLeft));
        }
        Ui.text(b.walletDetail, detail);
    }

    private void loadData() {
        call(ApiClient.api().stats(), s -> {
            if (s == null || b == null) return;
            b.statCases.statValue.setText(Money.number(s.activeCases));
            b.statHearings.statValue.setText(Money.number(s.hearingsThisWeek));
            b.statClients.statValue.setText(Money.number(s.clients));
        }, e -> { });
        call(ApiClient.api().hearings("upcoming", null, null), page -> {
            if (b == null) return;
            b.refresh.setRefreshing(false);
            List<Hearing> items = page != null && page.items != null ? page.items : java.util.Collections.emptyList();
            ReminderScheduler.scheduleAll(requireContext(), items);
            b.hearingsBox.removeAllViews();
            if (items.isEmpty()) {
                empty(b.hearingsBox, R.string.home_no_hearings);
                return;
            }
            for (int i = 0; i < Math.min(4, items.size()); i++) {
                Hearing h = items.get(i);
                RowAdapter.Row r = row(b.hearingsBox);
                long d = Dates.daysUntil(h.hearingDate);
                r.icon(R.drawable.ic_calendar, d <= 1 ? Tone.DANGER : d <= 3 ? Tone.WARNING : Tone.INFO)
                        .title(Texts.blank(h.caseTitle) ? h.caseNumber : h.caseTitle)
                        .subtitle(Texts.joinNonBlank(" • ", h.court, h.circuit))
                        .meta(Dates.dayTime(h.hearingDate))
                        .badge(d == 0 ? getString(R.string.today) : d == 1 ? getString(R.string.tomorrow) : null, Tone.DANGER);
                r.b.getRoot().setOnClickListener(v -> Router.open(requireContext(), "CASE", h.caseId));
            }
        }, e -> {
            if (b == null) return;
            b.refresh.setRefreshing(false);
            b.hearingsBox.removeAllViews();
            empty(b.hearingsBox, R.string.state_error_title);
        });
        call(ApiClient.api().legalUpdates(null), page -> {
            if (b == null) return;
            b.updatesBox.removeAllViews();
            List<LegalUpdate> items = page != null && page.items != null ? page.items : java.util.Collections.emptyList();
            if (items.isEmpty()) {
                empty(b.updatesBox, R.string.home_no_updates);
                return;
            }
            for (int i = 0; i < Math.min(3, items.size()); i++) {
                LegalUpdate u = items.get(i);
                RowAdapter.Row r = row(b.updatesBox);
                r.icon(R.drawable.ic_campaign, Tone.GOLD).title(u.title).subtitle(u.summary)
                        .meta(Dates.relative(u.publishedAt)).badge(u.typeLabel, Tone.GOLD);
                r.b.getRoot().setOnClickListener(v -> Router.openUpdate(requireContext(), u));
            }
        }, e -> {
            if (b == null) return;
            b.updatesBox.removeAllViews();
            empty(b.updatesBox, R.string.state_error_title);
        });
    }

    private RowAdapter.Row row(ViewGroup box) {
        ItemRowBinding rb = ItemRowBinding.inflate(getLayoutInflater(), box, true);
        RowAdapter.Row r = new RowAdapter.Row(rb);
        r.reset();
        return r;
    }

    private void empty(ViewGroup box, int text) {
        RowAdapter.Row r = row(box);
        r.icon(R.drawable.ic_inbox, Tone.NEUTRAL).title(getString(text)).chevron(false);
    }
}
