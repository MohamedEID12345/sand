package com.legalguide.app.ui.common;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.legalguide.app.core.util.Debouncer;
import com.legalguide.app.core.util.Texts;
import com.legalguide.app.data.model.Page;
import com.legalguide.app.databinding.FragmentListPageBinding;

import retrofit2.Call;

/** Template for top-level tab pages: big title, actions, search, filters, paginated list, FAB. */
public abstract class ListPageFragment<T> extends BaseFragment {
    protected FragmentListPageBinding b;
    protected ListController<T> controller;
    private final Debouncer debouncer = new Debouncer(350);
    private boolean loadedOnce;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        b = FragmentListPageBinding.inflate(inflater, container, false);
        return b.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        b.list.setLayoutManager(new LinearLayoutManager(requireContext()));
        RecyclerView.Adapter<?> a = createAdapter();
        b.list.setAdapter(a);
        controller = new ListController<>(getViewLifecycleOwner(), b.list, b.refresh, b.state, pagedAdapter(), this::load);
        setupPage();
        if (hasSearch()) {
            b.searchLayout.setVisibility(View.VISIBLE);
            b.searchInput.setHint(searchHint());
            b.searchInput.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int a1, int a2, int a3) {}
                @Override public void onTextChanged(CharSequence s, int a1, int a2, int a3) {}
                @Override public void afterTextChanged(Editable s) { debouncer.submit(() -> controller.refresh()); }
            });
            b.searchInput.setOnEditorActionListener((v, id, e) -> {
                if (id == EditorInfo.IME_ACTION_SEARCH) {
                    debouncer.cancel();
                    Ui.hideKeyboard(v);
                    controller.refresh();
                    return true;
                }
                return false;
            });
        }
        b.list.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                if (b.fab.getVisibility() != View.VISIBLE) return;
                if (dy > 8) b.fab.shrink();
                else if (dy < -8) b.fab.extend();
            }
        });
        controller.refresh();
        loadedOnce = true;
    }

    @Override
    public void onDestroyView() {
        debouncer.cancel();
        super.onDestroyView();
        b = null;
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (!hidden && loadedOnce && controller != null && refreshOnShow()) controller.refresh();
    }

    @Override
    public void reload() {
        if (controller != null) {
            b.list.smoothScrollToPosition(0);
            controller.refresh();
        }
    }

    protected abstract RecyclerView.Adapter<?> createAdapter();

    protected abstract PagedAdapter<T> pagedAdapter();

    protected abstract Call<Page<T>> load(@Nullable String cursor);

    protected abstract void setupPage();

    protected boolean hasSearch() { return false; }

    protected CharSequence searchHint() { return getString(com.legalguide.app.R.string.search_hint); }

    protected boolean refreshOnShow() { return true; }

    @Nullable
    protected String query() {
        return b == null ? null : Texts.nullIfBlank(b.searchInput.getText());
    }
}
