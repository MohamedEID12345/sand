package com.legalguide.app.ui.main;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.IdRes;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.legalguide.app.R;
import com.legalguide.app.core.net.ApiClient;
import com.legalguide.app.core.net.Calls;
import com.legalguide.app.core.session.Prefs;
import com.legalguide.app.core.session.Session;
import com.legalguide.app.core.util.Texts;
import com.legalguide.app.databinding.ActivityMainBinding;
import com.legalguide.app.ui.ai.AiChatsFragment;
import com.legalguide.app.ui.cases.CasesFragment;
import com.legalguide.app.ui.common.BaseActivity;
import com.legalguide.app.ui.common.BaseFragment;
import com.legalguide.app.ui.common.Router;
import com.legalguide.app.ui.library.LibraryFragment;

/** Bottom-navigation shell: Home, AI assistant, Cases, Library, More. */
public class MainActivity extends BaseActivity {
    public static final String EXTRA_REF_TYPE = "ref_type";
    public static final String EXTRA_REF_ID = "ref_id";
    public static final String EXTRA_TAB = "tab";
    private static final String STATE_TAB = "current_tab";
    private static final long BOOTSTRAP_TTL_MS = 30_000;

    private ActivityMainBinding b;
    @IdRes private int currentTab = R.id.nav_home;
    private long lastBootstrap;

    private final ActivityResultLauncher<String> notifPermission =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {});

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!ensureSession()) return;
        b = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        applyInsets(b.getRoot(), true, false);

        b.bottomNav.setOnItemSelectedListener(item -> {
            showTab(item.getItemId());
            return true;
        });
        b.bottomNav.setOnItemReselectedListener(item -> {
            Fragment f = getSupportFragmentManager().findFragmentByTag(tag(item.getItemId()));
            if (f instanceof BaseFragment) ((BaseFragment) f).reload();
        });

        int start = savedInstanceState != null ? savedInstanceState.getInt(STATE_TAB, R.id.nav_home) : R.id.nav_home;
        b.bottomNav.setSelectedItemId(start);
        if (savedInstanceState == null) showTab(start);

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (currentTab != R.id.nav_home) {
                    b.bottomNav.setSelectedItemId(R.id.nav_home);
                } else {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                    setEnabled(true);
                }
            }
        });

        loadLookups();
        askNotificationPermission();
        if (savedInstanceState == null) handleIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshBootstrap(false);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(STATE_TAB, currentTab);
    }

    public void selectTab(@IdRes int id) {
        b.bottomNav.setSelectedItemId(id);
    }

    /** Re-reads user/wallet/subscription/permissions/features from the server. */
    public void refreshBootstrap(boolean force) {
        long now = System.currentTimeMillis();
        if (!force && now - lastBootstrap < BOOTSTRAP_TTL_MS) return;
        lastBootstrap = now;
        Calls.enqueue(this, ApiClient.api().bootstrap(), boot -> Session.get().setBootstrap(boot), null);
    }

    private void loadLookups() {
        Calls.enqueue(this, ApiClient.api().aiCosts(), p -> {
            if (p != null) Session.get().setAiCosts(p.items);
        }, null);
        Calls.enqueue(this, ApiClient.api().caseMeta(), m -> Session.get().setCaseMeta(m), null);
    }

    private void handleIntent(@Nullable Intent i) {
        if (i == null) return;
        int tab = i.getIntExtra(EXTRA_TAB, 0);
        if (tab != 0) selectTab(tab);
        String type = i.getStringExtra(EXTRA_REF_TYPE);
        if (!Texts.blank(type)) {
            Router.open(this, type, i.getStringExtra(EXTRA_REF_ID));
            i.removeExtra(EXTRA_REF_TYPE);
        }
    }

    private void askNotificationPermission() {
        if (Build.VERSION.SDK_INT < 33 || Prefs.notifPermissionAsked()) return;
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return;
        Prefs.setNotifPermissionAsked();
        notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS);
    }

    private static String tag(int id) {
        return "tab_" + id;
    }

    private void showTab(@IdRes int id) {
        FragmentManager fm = getSupportFragmentManager();
        if (fm.isStateSaved()) return;
        FragmentTransaction tx = fm.beginTransaction().setReorderingAllowed(true)
                .setCustomAnimations(R.anim.lg_fade_in, R.anim.lg_fade_out);
        for (Fragment f : fm.getFragments()) {
            if (f.getTag() != null && f.getTag().startsWith("tab_") && !f.isHidden()) tx.hide(f);
        }
        Fragment target = fm.findFragmentByTag(tag(id));
        if (target == null) {
            target = create(id);
            tx.add(R.id.container, target, tag(id));
        } else {
            tx.show(target);
        }
        tx.commit();
        currentTab = id;
    }

    private Fragment create(@IdRes int id) {
        if (id == R.id.nav_ai) return new AiChatsFragment();
        if (id == R.id.nav_cases) return new CasesFragment();
        if (id == R.id.nav_library) return new LibraryFragment();
        if (id == R.id.nav_more) return new MoreFragment();
        return new HomeFragment();
    }
}
