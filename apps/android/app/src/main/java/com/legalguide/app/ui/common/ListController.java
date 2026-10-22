package com.legalguide.app.ui.common;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.LifecycleOwner;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.legalguide.app.R;
import com.legalguide.app.core.net.ApiError;
import com.legalguide.app.core.net.Calls;
import com.legalguide.app.data.model.Page;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;

/** Cursor pagination + pull-to-refresh + loading/empty/error states for a RecyclerView. */
public class ListController<T> {
    public interface Loader<T> { Call<Page<T>> load(@Nullable String cursor); }
    public interface OnLoaded<T> { void onLoaded(List<T> items, boolean firstPage); }

    private final LifecycleOwner owner;
    private final RecyclerView list;
    @Nullable private final SwipeRefreshLayout refresh;
    private final StateView state;
    private final PagedAdapter<T> adapter;
    private final Loader<T> loader;

    @Nullable private String cursor;
    private boolean loading;
    private boolean endReached;
    @Nullable private Call<Page<T>> inFlight;
    @Nullable private OnLoaded<T> onLoaded;

    @DrawableRes private int emptyIcon = R.drawable.ic_inbox;
    private CharSequence emptyTitle;
    @Nullable private CharSequence emptyMessage;
    @Nullable private CharSequence emptyAction;
    @Nullable private android.view.View.OnClickListener emptyListener;

    public ListController(LifecycleOwner owner, RecyclerView list, @Nullable SwipeRefreshLayout refresh,
                          StateView state, PagedAdapter<T> adapter, Loader<T> loader) {
        this.owner = owner;
        this.list = list;
        this.refresh = refresh;
        this.state = state;
        this.adapter = adapter;
        this.loader = loader;
        this.emptyTitle = list.getContext().getString(R.string.state_empty_title);
        if (refresh != null) {
            refresh.setColorSchemeResources(R.color.lg_primary, R.color.brand_gold);
            refresh.setOnRefreshListener(this::refresh);
        }
        list.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                if (dy <= 0 || loading || endReached) return;
                RecyclerView.LayoutManager lm = rv.getLayoutManager();
                if (lm instanceof LinearLayoutManager) {
                    int last = ((LinearLayoutManager) lm).findLastVisibleItemPosition();
                    if (last >= lm.getItemCount() - 5) load(false);
                }
            }
        });
    }

    public ListController<T> empty(@DrawableRes int icon, CharSequence title, @Nullable CharSequence message) {
        this.emptyIcon = icon;
        this.emptyTitle = title;
        this.emptyMessage = message;
        return this;
    }

    public ListController<T> emptyAction(CharSequence action, android.view.View.OnClickListener l) {
        this.emptyAction = action;
        this.emptyListener = l;
        return this;
    }

    public ListController<T> onLoaded(@Nullable OnLoaded<T> l) {
        this.onLoaded = l;
        return this;
    }

    public void refresh() {
        if (inFlight != null) inFlight.cancel();
        loading = false;
        cursor = null;
        endReached = false;
        load(true);
    }

    private void load(boolean first) {
        if (loading) return;
        loading = true;
        if (first && adapter.isEmpty() && (refresh == null || !refresh.isRefreshing())) state.loading();
        inFlight = Calls.enqueue(owner, loader.load(first ? null : cursor), page -> {
            loading = false;
            inFlight = null;
            if (refresh != null) refresh.setRefreshing(false);
            List<T> items = page != null && page.items != null ? page.items : new ArrayList<>();
            if (first) adapter.setItems(items);
            else adapter.addItems(items);
            cursor = page != null ? page.nextCursor : null;
            endReached = cursor == null || items.isEmpty();
            if (adapter.isEmpty()) state.empty(emptyIcon, emptyTitle, emptyMessage, emptyAction, emptyListener);
            else state.hide();
            if (first) list.scheduleLayoutAnimation();
            if (onLoaded != null) onLoaded.onLoaded(items, first);
        }, e -> {
            loading = false;
            inFlight = null;
            if (refresh != null) refresh.setRefreshing(false);
            if (first && adapter.isEmpty()) {
                state.error(e, this::refresh);
            } else {
                endReached = first ? endReached : true;
                FragmentActivity a = Ui.activity(list.getContext());
                if (!e.is(ApiError.UNAUTHORIZED)) ErrorUi.show(a, e);
            }
        });
    }
}
