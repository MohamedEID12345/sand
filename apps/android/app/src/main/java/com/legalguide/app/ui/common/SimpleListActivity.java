package com.legalguide.app.ui.common;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.ConcatAdapter;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.chip.ChipGroup;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.legalguide.app.core.util.Debouncer;
import com.legalguide.app.core.util.Texts;
import com.legalguide.app.data.model.Page;
import com.legalguide.app.databinding.ActivitySimpleListBinding;

import retrofit2.Call;

/** Template for paginated list screens (clients, hearings, invoices, tickets, ...). */
public abstract class SimpleListActivity<T> extends BaseActivity {
    protected ActivitySimpleListBinding b;
    protected RowAdapter<T> adapter;
    protected ListController<T> controller;
    @Nullable protected HeaderAdapter header;
    private final Debouncer debouncer = new Debouncer(350);

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!ensureSession()) return;
        b = ActivitySimpleListBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        applyInsets(b.getRoot());
        setupToolbar(b.toolbar, title(), true);

        adapter = new RowAdapter<>(this::bind).onClick(this::onItemClick);
        configureAdapter(adapter);
        RecyclerView.Adapter<?> root = adapter;
        View h = createHeader(b.list);
        if (h != null) {
            header = new HeaderAdapter(h);
            root = new ConcatAdapter(header, adapter);
        }
        b.list.setLayoutManager(new LinearLayoutManager(this));
        b.list.setAdapter(root);

        controller = new ListController<>(this, b.list, b.refresh, b.state, adapter, this::load);
        configure(controller);

        if (hasSearch()) {
            b.searchLayout.setVisibility(View.VISIBLE);
            b.searchInput.setHint(searchHint());
            b.searchInput.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int a, int c, int d) {}
                @Override public void onTextChanged(CharSequence s, int a, int c, int d) {}
                @Override public void afterTextChanged(Editable s) { debouncer.submit(() -> controller.refresh()); }
            });
            b.searchInput.setOnEditorActionListener((v, actionId, e) -> {
                if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                    debouncer.cancel();
                    Ui.hideKeyboard(v);
                    controller.refresh();
                    return true;
                }
                return false;
            });
        }
        if (setupFilters(b.filters)) b.filtersScroll.setVisibility(View.VISIBLE);
        setupFab(b.fab);
        b.list.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override public void onScrolled(RecyclerView rv, int dx, int dy) {
                if (b.fab.getVisibility() != View.VISIBLE) return;
                if (dy > 8) b.fab.shrink();
                else if (dy < -8) b.fab.extend();
            }
        });
        onReady();
        controller.refresh();
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        if (controller != null && reloadOnReturn()) controller.refresh();
    }

    protected abstract CharSequence title();

    protected abstract Call<Page<T>> load(@Nullable String cursor);

    protected abstract void bind(RowAdapter.Row row, T item);

    protected void onItemClick(T item) {}

    protected void configureAdapter(RowAdapter<T> a) {}

    protected void configure(ListController<T> c) {}

    protected boolean hasSearch() { return false; }

    protected CharSequence searchHint() { return getString(com.legalguide.app.R.string.search_hint); }

    /** Add filter chips; return true to show the filter bar. */
    protected boolean setupFilters(ChipGroup group) { return false; }

    protected void setupFab(ExtendedFloatingActionButton fab) {}

    @Nullable
    protected View createHeader(ViewGroup parent) { return null; }

    protected void onReady() {}

    protected boolean reloadOnReturn() { return true; }

    @Nullable
    protected String query() {
        return b == null ? null : Texts.nullIfBlank(b.searchInput.getText());
    }

    protected void reload() {
        controller.refresh();
    }
}
