package com.legalguide.app.ui.search;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.inputmethod.EditorInfo;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.legalguide.app.R;
import com.legalguide.app.core.net.ApiClient;
import com.legalguide.app.core.util.Debouncer;
import com.legalguide.app.core.util.Texts;
import com.legalguide.app.data.model.LibraryItem;
import com.legalguide.app.data.model.SearchGroup;
import com.legalguide.app.databinding.ActivitySearchBinding;
import com.legalguide.app.ui.common.BaseActivity;
import com.legalguide.app.ui.common.Kinds;
import com.legalguide.app.ui.common.RowAdapter;
import com.legalguide.app.ui.common.Router;
import com.legalguide.app.ui.common.Ui;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;

/** Global search across cases, clients, documents, contracts and the legal library (tenant-scoped on the server). */
public class SearchActivity extends BaseActivity {
    private ActivitySearchBinding b;
    private RowAdapter<LibraryItem> adapter;
    private final Debouncer debouncer = new Debouncer(400);
    @Nullable private Call<?> inFlight;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!ensureSession()) return;
        b = ActivitySearchBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        applyInsets(b.getRoot());
        b.back.setOnClickListener(v -> finish());

        adapter = new RowAdapter<LibraryItem>((row, it) -> row.icon(Kinds.icon(it.type), Kinds.tone(it.type))
                .title(it.title).subtitle(Texts.blank(it.snippet) ? it.subtitle : it.snippet)
                .meta(Kinds.label(this, it.type)))
                .onClick(it -> Router.open(this, it.type, it.id));
        b.list.setLayoutManager(new LinearLayoutManager(this));
        b.list.setAdapter(adapter);
        b.state.empty(R.drawable.ic_search, getString(R.string.search_title), getString(R.string.search_intro));

        b.input.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int c, int d) {}
            @Override public void onTextChanged(CharSequence s, int a, int c, int d) {}
            @Override public void afterTextChanged(Editable s) { debouncer.submit(() -> search(false)); }
        });
        b.input.setOnEditorActionListener((v, id, e) -> {
            if (id == EditorInfo.IME_ACTION_SEARCH) {
                debouncer.cancel();
                Ui.hideKeyboard(v);
                search(true);
                return true;
            }
            return false;
        });
        b.input.requestFocus();
    }

    @Override
    protected void onDestroy() {
        debouncer.cancel();
        super.onDestroy();
    }

    private void search(boolean force) {
        String q = Texts.trim(b.input.getText());
        if (inFlight != null) inFlight.cancel();
        if (q.length() < 2) {
            adapter.clear();
            b.state.empty(R.drawable.ic_search, getString(R.string.search_title), getString(R.string.search_intro));
            return;
        }
        b.progress.show();
        inFlight = call(ApiClient.api().search(q), res -> {
            b.progress.hide();
            List<LibraryItem> all = new ArrayList<>();
            adapter.clear();
            if (res != null && res.groups != null) {
                for (SearchGroup g : res.groups) {
                    if (g.items == null || g.items.isEmpty()) continue;
                    adapter.addHeader(Texts.orDash(g.label));
                    adapter.addItems(g.items);
                    all.addAll(g.items);
                }
            }
            if (all.isEmpty()) b.state.empty(R.drawable.ic_search, getString(R.string.search_no_results, q), null);
            else b.state.hide();
        }, e -> {
            b.progress.hide();
            if (force || adapter.isEmpty()) b.state.error(e, () -> search(true));
        });
    }
}
