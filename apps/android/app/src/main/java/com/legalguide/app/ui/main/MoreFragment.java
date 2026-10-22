package com.legalguide.app.ui.main;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.google.android.material.card.MaterialCardView;
import com.legalguide.app.BuildConfig;
import com.legalguide.app.R;
import com.legalguide.app.core.session.Session;
import com.legalguide.app.core.util.Intents;
import com.legalguide.app.core.util.Money;
import com.legalguide.app.core.util.Texts;
import com.legalguide.app.data.model.AppConfig;
import com.legalguide.app.data.model.Bootstrap;
import com.legalguide.app.databinding.FragmentMoreBinding;
import com.legalguide.app.databinding.ItemRowBinding;
import com.legalguide.app.databinding.ItemSectionHeaderBinding;
import com.legalguide.app.ui.billing.InvoicesActivity;
import com.legalguide.app.ui.billing.PlansActivity;
import com.legalguide.app.ui.billing.WalletActivity;
import com.legalguide.app.ui.clients.ClientsActivity;
import com.legalguide.app.ui.common.BaseFragment;
import com.legalguide.app.ui.common.RowAdapter;
import com.legalguide.app.ui.common.Tone;
import com.legalguide.app.ui.common.Ui;
import com.legalguide.app.ui.contracts.ContractTemplatesActivity;
import com.legalguide.app.ui.contracts.MyContractsActivity;
import com.legalguide.app.ui.hearings.HearingsActivity;
import com.legalguide.app.ui.notifications.NotificationsActivity;
import com.legalguide.app.ui.profile.ProfileActivity;
import com.legalguide.app.ui.profile.SettingsActivity;
import com.legalguide.app.ui.profile.TeamActivity;
import com.legalguide.app.ui.support.TicketsActivity;

public class MoreFragment extends BaseFragment {
    private FragmentMoreBinding b;
    @Nullable private RowAdapter.Row walletRow;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        b = FragmentMoreBinding.inflate(inflater, container, false);
        return b.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        b.profileCard.setOnClickListener(v -> open(ProfileActivity.class));
        b.logout.setOnClickListener(v -> SettingsActivity.confirmLogout(requireActivity()));
        b.version.setText(getString(R.string.more_version, BuildConfig.VERSION_NAME));
        buildMenu();
        Session.get().bootstrap().observe(getViewLifecycleOwner(), this::bind);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        b = null;
        walletRow = null;
    }

    private void buildMenu() {
        b.sections.removeAllViews();
        Session s = Session.get();

        LinearLayout account = section(R.string.more_section_account);
        walletRow = item(account, R.drawable.ic_wallet, Tone.GOLD, R.string.wallet_title, v -> open(WalletActivity.class));
        item(account, R.drawable.ic_crown, Tone.GOLD, R.string.plans_title, v ->
                startActivity(new Intent(requireContext(), PlansActivity.class)));
        item(account, R.drawable.ic_receipt, Tone.NEUTRAL, R.string.invoices_title, v -> open(InvoicesActivity.class));
        item(account, R.drawable.ic_bell, Tone.INFO, R.string.notifications_title, v -> open(NotificationsActivity.class));

        LinearLayout work = section(R.string.more_section_work);
        item(work, R.drawable.ic_people, Tone.PRIMARY, R.string.clients_title, v -> open(ClientsActivity.class));
        item(work, R.drawable.ic_calendar, Tone.WARNING, R.string.hearings_title, v -> open(HearingsActivity.class));
        if (s.feature("contracts")) {
            item(work, R.drawable.ic_contract, Tone.SUCCESS, R.string.contracts_templates_title, v -> open(ContractTemplatesActivity.class));
            item(work, R.drawable.ic_document, Tone.SUCCESS, R.string.contracts_mine_title, v -> open(MyContractsActivity.class));
        }

        if (s.can("office.members.read") || s.can("office.manage")) {
            LinearLayout office = section(R.string.more_section_office);
            item(office, R.drawable.ic_team, Tone.PRIMARY, R.string.team_title, v -> open(TeamActivity.class));
        }

        LinearLayout support = section(R.string.more_section_support);
        item(support, R.drawable.ic_headset, Tone.INFO, R.string.tickets_title, v -> open(TicketsActivity.class));
        AppConfig cfg = s.config();
        if (cfg != null && !Texts.blank(cfg.supportWhatsapp)) {
            item(support, R.drawable.ic_chat, Tone.SUCCESS, R.string.support_whatsapp, v -> Intents.whatsapp(requireContext(), cfg.supportWhatsapp, null));
        }
        if (cfg != null && !Texts.blank(cfg.supportEmail)) {
            item(support, R.drawable.ic_email, Tone.NEUTRAL, R.string.support_email, v -> Intents.email(requireContext(), cfg.supportEmail, getString(R.string.app_name)));
        }

        LinearLayout app = section(R.string.more_section_app);
        item(app, R.drawable.ic_settings, Tone.NEUTRAL, R.string.settings_title, v -> open(SettingsActivity.class));
        if (cfg != null && !Texts.blank(cfg.privacyUrl)) {
            item(app, R.drawable.ic_shield, Tone.NEUTRAL, R.string.settings_privacy, v -> Intents.openUrl(requireContext(), cfg.privacyUrl));
        }
        if (cfg != null && !Texts.blank(cfg.termsUrl)) {
            item(app, R.drawable.ic_document, Tone.NEUTRAL, R.string.settings_terms, v -> Intents.openUrl(requireContext(), cfg.termsUrl));
        }
    }

    private void bind(@Nullable Bootstrap boot) {
        if (b == null || boot == null || boot.user == null) return;
        b.name.setText(boot.user.name);
        b.email.setText(boot.user.email);
        b.avatar.setText(Texts.initials(boot.user.name));
        String role = Texts.joinNonBlank(" • ", boot.user.officeName, boot.user.officeRoleLabel);
        Ui.text(b.role, role);
        if (!Texts.blank(role)) Tone.PRIMARY.badge(b.role);
        if (walletRow != null && boot.wallet != null) {
            walletRow.trailing(getString(R.string.credits_count, Money.number(boot.wallet.remainingCredits)));
        }
        buildMenuIfPermissionsChanged();
    }

    private String lastPermissions = "";

    private void buildMenuIfPermissionsChanged() {
        Bootstrap boot = Session.get().current();
        String p = boot == null || boot.permissions == null ? "" : boot.permissions.toString() + boot.features;
        if (!p.equals(lastPermissions)) {
            lastPermissions = p;
            buildMenu();
            if (walletRow != null && boot != null && boot.wallet != null) {
                walletRow.trailing(getString(R.string.credits_count, Money.number(boot.wallet.remainingCredits)));
            }
        }
    }

    private LinearLayout section(@StringRes int title) {
        ItemSectionHeaderBinding h = ItemSectionHeaderBinding.inflate(getLayoutInflater(), b.sections, true);
        h.sectionTitle.setText(title);
        h.sectionTitle.setPadding(0, Ui.dp(requireContext(), 20), 0, Ui.dp(requireContext(), 6));
        MaterialCardView card = new MaterialCardView(requireContext());
        LinearLayout inner = new LinearLayout(requireContext());
        inner.setOrientation(LinearLayout.VERTICAL);
        card.addView(inner);
        b.sections.addView(card, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return inner;
    }

    private RowAdapter.Row item(LinearLayout box, @DrawableRes int icon, Tone tone, @StringRes int title, View.OnClickListener l) {
        ItemRowBinding rb = ItemRowBinding.inflate(getLayoutInflater(), box, true);
        rb.getRoot().setMinimumHeight(Ui.dp(requireContext(), 60));
        RowAdapter.Row r = new RowAdapter.Row(rb);
        r.reset();
        r.icon(icon, tone).title(getString(title));
        rb.getRoot().setOnClickListener(l);
        return r;
    }

    private void open(Class<?> c) {
        startActivity(new Intent(requireContext(), c));
    }
}
