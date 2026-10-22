package com.legalguide.app.ui.cases;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.legalguide.app.data.model.Page;
import com.legalguide.app.databinding.FragmentTabListBinding;
import com.legalguide.app.ui.common.BaseFragment;
import com.legalguide.app.ui.common.ListController;
import com.legalguide.app.ui.common.RowAdapter;

import retrofit2.Call;

/** Paginated list inside a case tab. */
public abstract class CaseTabListFragment<T> extends BaseFragment implements CaseDetailActivity.Tab {
    protected FragmentTabListBinding b;
    protected CaseViewModel vm;
    protected RowAdapter<T> adapter;
    protected ListController<T> controller;
    private int lastChange = -1;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        b = FragmentTabListBinding.inflate(inflater, container, false);
        return b.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        vm = new ViewModelProvider(requireActivity()).get(CaseViewModel.class);
        adapter = new RowAdapter<>(this::bind);
        adapter.onClick(this::onItemClick).onLongClick(this::onItemLongClick);
        b.list.setLayoutManager(new LinearLayoutManager(requireContext()));
        b.list.setAdapter(adapter);
        controller = new ListController<>(getViewLifecycleOwner(), b.list, b.refresh, b.state, adapter,
                cursor -> load(vm.caseId(), cursor));
        configure(controller);
        vm.changes().observe(getViewLifecycleOwner(), n -> {
            int v = n == null ? 0 : n;
            if (v != lastChange) {
                lastChange = v;
                controller.refresh();
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        if (getActivity() instanceof CaseDetailActivity) ((CaseDetailActivity) getActivity()).updateFab();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        b = null;
    }

    protected abstract Call<Page<T>> load(String caseId, @Nullable String cursor);

    protected abstract void bind(RowAdapter.Row row, T item);

    protected void onItemClick(T item) {}

    protected void onItemLongClick(T item) {}

    protected void configure(ListController<T> c) {}

    @Override
    public boolean fabVisible() {
        return true;
    }
}
