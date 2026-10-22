package com.legalguide.app.ui.ai;

import android.view.LayoutInflater;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.legalguide.app.R;
import com.legalguide.app.core.net.ApiClient;
import com.legalguide.app.core.session.Session;
import com.legalguide.app.core.util.Dates;
import com.legalguide.app.core.util.Texts;
import com.legalguide.app.data.model.AiChat;
import com.legalguide.app.data.model.Page;
import com.legalguide.app.databinding.ItemModeCardBinding;
import com.legalguide.app.ui.common.Dialogs;
import com.legalguide.app.ui.common.ListPageFragment;
import com.legalguide.app.ui.common.PagedAdapter;
import com.legalguide.app.ui.common.RowAdapter;
import com.legalguide.app.ui.common.Ui;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;

public class AiChatsFragment extends ListPageFragment<AiChat> {
    private RowAdapter<AiChat> adapter;
    private final List<ItemModeCardBinding> modeCards = new ArrayList<>();

    @Override
    protected RecyclerView.Adapter<?> createAdapter() {
        adapter = new RowAdapter<AiChat>((row, c) -> {
            AiModes m = AiModes.of(c.mode);
            row.icon(m.icon, m.tone)
                    .title(Texts.blank(c.title) ? getString(m.title) : c.title)
                    .subtitle(c.lastMessage)
                    .meta(Texts.joinNonBlank(" • ", Dates.relative(c.updatedAt), c.caseTitle))
                    .badge(getString(m.title), m.tone);
        }).onClick(c -> ChatActivity.open(requireContext(), c.id))
                .onLongClick(c -> Dialogs.confirmDelete(requireContext(), getString(R.string.chat_item), () ->
                        call(ApiClient.api().deleteChat(c.id), ok -> controller.refresh())));
        return adapter;
    }

    @Override
    protected PagedAdapter<AiChat> pagedAdapter() {
        return adapter;
    }

    @Override
    protected Call<Page<AiChat>> load(@Nullable String cursor) {
        return ApiClient.api().chats(cursor);
    }

    @Override
    protected void setupPage() {
        b.pageTitle.setText(R.string.ai_title);
        Ui.text(b.pageSubtitle, getString(R.string.ai_subtitle));
        b.pageAction1.setVisibility(View.VISIBLE);
        b.pageAction1.setIconResource(R.drawable.ic_add);
        b.pageAction1.setContentDescription(getString(R.string.ai_new_chat));
        b.pageAction1.setOnClickListener(v -> ChatActivity.start(requireContext(), AiModes.QUESTION.code, null));

        HorizontalScrollView hs = new HorizontalScrollView(requireContext());
        hs.setHorizontalScrollBarEnabled(false);
        hs.setClipToPadding(false);
        int pad = getResources().getDimensionPixelSize(R.dimen.screen_padding);
        hs.setPadding(pad, 0, pad, Ui.dp(requireContext(), 12));
        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        hs.addView(row);
        LayoutInflater inf = getLayoutInflater();
        for (AiModes m : AiModes.values()) {
            if (!m.enabled()) continue;
            ItemModeCardBinding card = ItemModeCardBinding.inflate(inf, row, true);
            card.modeIcon.setImageResource(m.icon);
            m.tone.tile(card.modeTile, card.modeIcon);
            card.modeTitle.setText(m.title);
            card.modeDesc.setText(m.desc);
            card.getRoot().setTag(m);
            card.getRoot().setOnClickListener(v -> ChatActivity.start(requireContext(), m.code, null));
            modeCards.add(card);
        }
        b.pageHeader.addView(hs);
        refreshCosts();
        Session.get().bootstrap().observe(getViewLifecycleOwner(), boot -> refreshCosts());

        controller.empty(R.drawable.ic_chat, getString(R.string.ai_empty_title), getString(R.string.ai_empty_message));
    }

    private void refreshCosts() {
        for (ItemModeCardBinding c : modeCards) {
            AiModes m = (AiModes) c.getRoot().getTag();
            Ui.text(c.modeCost, m.costLabel(requireContext()));
        }
    }

    @Override
    public void onDestroyView() {
        modeCards.clear();
        super.onDestroyView();
    }
}
