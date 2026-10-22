package com.legalguide.app.ui.library;

import android.content.Intent;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.chip.Chip;
import com.legalguide.app.R;
import com.legalguide.app.core.net.ApiClient;
import com.legalguide.app.core.util.Dates;
import com.legalguide.app.core.util.Texts;
import com.legalguide.app.data.model.LibraryCategory;
import com.legalguide.app.data.model.LibraryItem;
import com.legalguide.app.data.model.Page;
import com.legalguide.app.ui.common.Kinds;
import com.legalguide.app.ui.common.ListPageFragment;
import com.legalguide.app.ui.common.PagedAdapter;
import com.legalguide.app.ui.common.RowAdapter;
import com.legalguide.app.ui.common.Ui;

import retrofit2.Call;

/** Legal library: laws, articles, judgments, principles, books — searched server-side (RAG index). */
public class LibraryFragment extends ListPageFragment<LibraryItem> {
    private static final String[][] TYPES = {
            {null, null}, {"LAW", null}, {"ARTICLE", null}, {"JUDGMENT", null}, {"PRINCIPLE", null}, {"BOOK", null}};

    private RowAdapter<LibraryItem> adapter;
    @Nullable private String type;
    @Nullable private String categoryId;

    @Override
    protected RecyclerView.Adapter<?> createAdapter() {
        adapter = new RowAdapter<LibraryItem>((row, it) -> bindItem(row, it))
                .onClick(it -> SourceViewerActivity.start(requireContext(), it.type, it.id, null));
        return adapter;
    }

    public static void bindItem(RowAdapter.Row row, LibraryItem it) {
        android.content.Context c = row.b.getRoot().getContext();
        String status = Kinds.statusLabel(c, it.status);
        row.icon(Kinds.icon(it.type), Kinds.tone(it.type))
                .title(it.title)
                .subtitle(Texts.blank(it.snippet) ? it.subtitle : it.snippet)
                .meta(Texts.joinNonBlank(" • ", Kinds.label(c, it.type), it.categoryName,
                        it.effectiveDate == null ? null : Dates.day(it.effectiveDate)))
                .badge(status, Kinds.statusTone(it.status));
    }

    @Override
    protected PagedAdapter<LibraryItem> pagedAdapter() {
        return adapter;
    }

    @Override
    protected Call<Page<LibraryItem>> load(@Nullable String cursor) {
        return ApiClient.api().librarySearch(query(), type, categoryId, cursor);
    }

    @Override
    protected boolean hasSearch() {
        return true;
    }

    @Override
    protected CharSequence searchHint() {
        return getString(R.string.library_search_hint);
    }

    @Override
    protected void setupPage() {
        b.pageTitle.setText(R.string.library_title);
        Ui.text(b.pageSubtitle, getString(R.string.library_subtitle));
        b.pageAction1.setVisibility(View.VISIBLE);
        b.pageAction1.setIconResource(R.drawable.ic_campaign);
        b.pageAction1.setContentDescription(getString(R.string.updates_title));
        b.pageAction1.setOnClickListener(v -> startActivity(new Intent(requireContext(), LegalUpdatesActivity.class)));
        controller.empty(R.drawable.ic_library, getString(R.string.library_empty_title), getString(R.string.library_empty_message));

        b.filtersScroll.setVisibility(View.VISIBLE);
        int[] labels = {R.string.filter_all, R.string.kind_laws, R.string.kind_articles, R.string.kind_judgments,
                R.string.kind_principles, R.string.kind_books};
        for (int i = 0; i < TYPES.length; i++) {
            String t = TYPES[i][0];
            Chip c = Ui.chip(b.filters, getString(labels[i]), true);
            c.setChecked(t == null);
            c.setOnClickListener(v -> {
                type = t;
                controller.refresh();
            });
        }
        call(ApiClient.api().libraryCategories(), page -> {
            if (b == null || page == null || page.items == null || page.items.isEmpty()) return;
            b.filters2Scroll.setVisibility(View.VISIBLE);
            for (LibraryCategory cat : page.items) {
                Chip c = Ui.chip(b.filters2, cat.name, true);
                c.setOnClickListener(v -> {
                    categoryId = c.isChecked() ? cat.id : null;
                    controller.refresh();
                });
            }
        }, e -> { });
    }

    @Override
    protected boolean refreshOnShow() {
        return false;
    }
}
