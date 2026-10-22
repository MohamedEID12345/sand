package com.legalguide.app.ui.library;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.Nullable;

import com.legalguide.app.R;
import com.legalguide.app.core.net.ApiClient;
import com.legalguide.app.core.session.Session;
import com.legalguide.app.core.util.Intents;
import com.legalguide.app.core.util.Md;
import com.legalguide.app.core.util.Texts;
import com.legalguide.app.data.model.LibraryItem;
import com.legalguide.app.data.model.SourceView;
import com.legalguide.app.databinding.ActivitySourceViewerBinding;
import com.legalguide.app.databinding.ItemRowBinding;
import com.legalguide.app.ui.ai.AiModes;
import com.legalguide.app.ui.ai.ChatActivity;
import com.legalguide.app.ui.common.BaseActivity;
import com.legalguide.app.ui.common.Kinds;
import com.legalguide.app.ui.common.RowAdapter;
import com.legalguide.app.ui.common.Tone;
import com.legalguide.app.ui.common.Ui;

import java.util.List;

/** Reads any legal source (law, article, judgment, principle, book page, update). */
public class SourceViewerActivity extends BaseActivity {
    private static final String EXTRA_TYPE = "type", EXTRA_ID = "id", EXTRA_PAGE = "page";

    private ActivitySourceViewerBinding b;
    private String type;
    private String id;
    @Nullable private Integer page;
    @Nullable private SourceView source;

    public static void start(Context c, String type, String id, @Nullable Integer page) {
        Intent i = new Intent(c, SourceViewerActivity.class).putExtra(EXTRA_TYPE, type).putExtra(EXTRA_ID, id);
        if (page != null) i.putExtra(EXTRA_PAGE, page.intValue());
        c.startActivity(i);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!ensureSession()) return;
        b = ActivitySourceViewerBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        applyInsets(b.getRoot());
        type = getIntent().getStringExtra(EXTRA_TYPE);
        id = getIntent().getStringExtra(EXTRA_ID);
        page = getIntent().hasExtra(EXTRA_PAGE) ? getIntent().getIntExtra(EXTRA_PAGE, 1) : null;
        setupToolbar(b.appBar.toolbar, Kinds.label(this, type), true);

        b.appBar.toolbar.inflateMenu(R.menu.menu_source);
        b.appBar.toolbar.setOnMenuItemClickListener(item -> {
            if (source == null) return true;
            int itemId = item.getItemId();
            if (itemId == R.id.action_copy) Intents.copy(this, asText(source));
            else if (itemId == R.id.action_share) Intents.shareText(this, source.title, asText(source));
            else if (itemId == R.id.action_open && !Texts.blank(source.url)) Intents.openUrl(this, source.url);
            return true;
        });
        b.askAi.setOnClickListener(v -> {
            if (source != null) ChatActivity.startWithPrompt(this, AiModes.QUESTION.code, null,
                    getString(R.string.source_ask_prompt, source.title));
        });
        Ui.show(b.askAi, Session.get().feature("ai"));
        b.prev.setOnClickListener(v -> go(-1));
        b.next.setOnClickListener(v -> go(1));
        load();
    }

    private void go(int delta) {
        int p = (page == null ? 1 : page) + delta;
        if (source != null && source.pageCount != null && (p < 1 || p > source.pageCount)) return;
        page = p;
        load();
    }

    private void load() {
        b.state.loading();
        call(ApiClient.api().source(type, id, page), s -> {
            if (s == null) return;
            source = s;
            b.state.hide();
            render(s);
            b.scroll.scrollTo(0, 0);
        }, e -> b.state.error(e, this::load));
    }

    private void render(SourceView s) {
        b.appBar.toolbar.setTitle(Kinds.label(this, s.type));
        b.title.setText(s.title);
        Ui.text(b.subtitle, s.subtitle);
        Ui.text(b.kind, Kinds.label(this, s.type));
        Kinds.tone(s.type).badge(b.kind);
        String status = Kinds.statusLabel(this, s.status);
        Ui.text(b.status, status);
        if (status != null) Kinds.statusTone(s.status).badge(b.status);
        if (s.verified != null) {
            b.verified.setVisibility(View.VISIBLE);
            b.verified.setText(s.verified ? R.string.source_verified : R.string.source_unverified);
            (s.verified ? Tone.SUCCESS : Tone.WARNING).badge(b.verified);
        } else {
            b.verified.setVisibility(View.GONE);
        }
        b.meta.removeAllViews();
        Ui.kvList(b.meta, s.meta);
        Ui.show(b.metaCard, s.meta != null && !s.meta.isEmpty());
        Md.render(b.body, s.body);
        Ui.show(b.bodyCard, !Texts.blank(s.body));

        boolean paged = s.pageCount != null && s.pageCount > 1;
        Ui.show(b.pager, paged);
        if (paged) {
            int p = s.page != null ? s.page : (page != null ? page : 1);
            page = p;
            b.pageLabel.setText(getString(R.string.source_page_of, p, s.pageCount));
            b.prev.setEnabled(p > 1);
            b.next.setEnabled(p < s.pageCount);
        }
        list(b.sections, b.sectionsTitle, s.sections);
        list(b.related, b.relatedTitle, s.related);
    }

    private void list(android.widget.LinearLayout box, View title, @Nullable List<LibraryItem> items) {
        box.removeAllViews();
        boolean any = items != null && !items.isEmpty();
        Ui.show(title, any);
        Ui.show(box, any);
        if (!any) return;
        for (LibraryItem it : items) {
            ItemRowBinding rb = ItemRowBinding.inflate(getLayoutInflater(), box, true);
            RowAdapter.Row r = new RowAdapter.Row(rb);
            r.reset();
            LibraryFragment.bindItem(r, it);
            rb.getRoot().setOnClickListener(v -> start(this, it.type, it.id, null));
        }
    }

    private String asText(SourceView s) {
        return Texts.joinNonBlank("\n\n", s.title, s.subtitle, s.body) + "\n\n— " + getString(R.string.app_name);
    }
}
