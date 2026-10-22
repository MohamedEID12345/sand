package com.legalguide.app.ui.cases;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.tabs.TabLayoutMediator;
import com.legalguide.app.R;
import com.legalguide.app.core.net.ApiClient;
import com.legalguide.app.core.session.Session;
import com.legalguide.app.core.util.Texts;
import com.legalguide.app.data.model.LegalCase;
import com.legalguide.app.databinding.ActivityCaseDetailBinding;
import com.legalguide.app.ui.ai.AiModes;
import com.legalguide.app.ui.ai.ChatActivity;
import com.legalguide.app.ui.common.BaseActivity;
import com.legalguide.app.ui.common.Dialogs;
import com.legalguide.app.ui.common.Tone;
import com.legalguide.app.ui.common.Ui;

/** Case workspace: overview & deadlines, hearings, documents, AI analysis, notes. */
public class CaseDetailActivity extends BaseActivity {
    private static final String EXTRA_ID = "case_id";
    private static final int[] TABS = {R.string.case_tab_overview, R.string.case_tab_hearings, R.string.case_tab_documents,
            R.string.case_tab_analysis, R.string.case_tab_notes};

    private ActivityCaseDetailBinding b;
    private CaseViewModel vm;
    private boolean firstResume = true;

    /** Implemented by tabs that own the floating action. */
    public interface Tab {
        int fabText();
        int fabIcon();
        void onFab();
        boolean fabVisible();
    }

    public static void start(Context c, String caseId) {
        c.startActivity(new Intent(c, CaseDetailActivity.class).putExtra(EXTRA_ID, caseId));
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!ensureSession()) return;
        b = ActivityCaseDetailBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        applyInsets(b.getRoot());
        setupToolbar(b.toolbar, R.string.case_title, true);

        String id = getIntent().getStringExtra(EXTRA_ID);
        if (id == null) {
            finish();
            return;
        }
        vm = new ViewModelProvider(this).get(CaseViewModel.class);
        vm.init(id);

        b.pager.setAdapter(new FragmentStateAdapter(this) {
            @NonNull
            @Override
            public Fragment createFragment(int position) {
                switch (position) {
                    case 1: return new CaseHearingsFragment();
                    case 2: return new CaseDocumentsFragment();
                    case 3: return new CaseAnalysesFragment();
                    case 4: return new CaseNotesFragment();
                    default: return new CaseOverviewFragment();
                }
            }

            @Override
            public int getItemCount() {
                return TABS.length;
            }
        });
        b.pager.setOffscreenPageLimit(1);
        new TabLayoutMediator(b.tabs, b.pager, (tab, pos) -> tab.setText(TABS[pos])).attach();
        b.pager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                b.pager.post(() -> updateFab());
            }
        });
        b.fab.setOnClickListener(v -> {
            Tab t = currentTab();
            if (t != null) t.onFab();
        });

        b.toolbar.inflateMenu(R.menu.menu_case);
        b.toolbar.getMenu().findItem(R.id.action_edit).setVisible(Session.get().can("cases.update"));
        b.toolbar.getMenu().findItem(R.id.action_delete).setVisible(Session.get().can("cases.delete"));
        b.toolbar.setOnMenuItemClickListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.action_edit) {
                CaseFormActivity.edit(this, id);
            } else if (itemId == R.id.action_delete) {
                Dialogs.confirmDelete(this, getString(R.string.kind_case), () ->
                        call(ApiClient.api().deleteCase(id), ok -> finish()));
            } else if (itemId == R.id.action_ask_ai) {
                ChatActivity.start(this, AiModes.QUESTION.code, id);
            }
            return true;
        });

        b.state.loading();
        vm.legalCase().observe(this, this::bind);
        vm.error().observe(this, e -> {
            if (e != null && vm.legalCase().getValue() == null) b.state.error(e, vm::reload);
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!firstResume && vm != null) vm.reload();
        firstResume = false;
    }

    private void bind(@Nullable LegalCase c) {
        if (c == null) return;
        b.state.hide();
        b.toolbar.setTitle(getString(R.string.case_title_n, c.displayTitle()));
        b.caseType.setText(Texts.orDash(c.caseTypeName));
        Ui.text(b.caseParties, Texts.joinNonBlank(getString(R.string.case_vs), c.clientName, c.opponent));
        Ui.text(b.caseCourt, Texts.joinNonBlank(" • ", c.court, c.circuit));
        Ui.text(b.caseStatus, c.statusLabel);
        if (!Texts.blank(c.statusLabel)) Tone.forStatus(c.status).badge(b.caseStatus);
        Ui.text(b.casePriority, c.priorityLabel);
        if (!Texts.blank(c.priorityLabel)) Tone.forStatus(c.priority).badge(b.casePriority);
        updateFab();
    }

    public void updateFab() {
        Tab t = currentTab();
        if (t == null || !t.fabVisible()) {
            b.fab.setVisibility(View.GONE);
            return;
        }
        b.fab.setVisibility(View.VISIBLE);
        b.fab.setText(t.fabText());
        b.fab.setIconResource(t.fabIcon());
        b.fab.extend();
    }

    @Nullable
    private Tab currentTab() {
        Fragment f = getSupportFragmentManager().findFragmentByTag("f" + b.pager.getCurrentItem());
        return f instanceof Tab ? (Tab) f : null;
    }
}
