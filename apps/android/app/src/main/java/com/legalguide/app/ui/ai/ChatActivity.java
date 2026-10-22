package com.legalguide.app.ui.ai;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.chip.Chip;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.legalguide.app.BuildConfig;
import com.legalguide.app.R;
import com.legalguide.app.core.net.ApiClient;
import com.legalguide.app.core.net.ApiError;
import com.legalguide.app.core.net.UriRequestBody;
import com.legalguide.app.core.session.Session;
import com.legalguide.app.core.util.Intents;
import com.legalguide.app.core.util.Texts;
import com.legalguide.app.data.model.AiChat;
import com.legalguide.app.data.model.AiMessage;
import com.legalguide.app.data.model.AppConfig;
import com.legalguide.app.data.model.Attachment;
import com.legalguide.app.data.model.Citation;
import com.legalguide.app.data.model.Wallet;
import com.legalguide.app.databinding.ActivityChatBinding;
import com.legalguide.app.ui.common.BaseActivity;
import com.legalguide.app.ui.common.Dialogs;
import com.legalguide.app.ui.common.Router;
import com.legalguide.app.ui.common.Ui;

import java.util.List;

/** AI conversation in any mode, with attachments and verified citations. */
public class ChatActivity extends BaseActivity implements MessageAdapter.Listener {
    private static final String EXTRA_CHAT_ID = "chat_id", EXTRA_MODE = "mode", EXTRA_CASE_ID = "case_id", EXTRA_PROMPT = "prompt";

    private ActivityChatBinding b;
    private ChatViewModel vm;
    private MessageAdapter adapter;
    private boolean confirmedCost;

    private final ActivityResultLauncher<String[]> pickFile = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(), this::onFilePicked);

    public static void start(Context c, String mode, @Nullable String caseId) {
        c.startActivity(new Intent(c, ChatActivity.class).putExtra(EXTRA_MODE, mode).putExtra(EXTRA_CASE_ID, caseId));
    }

    /** New chat pre-filled with a prompt (e.g. "ask about this article"). */
    public static void startWithPrompt(Context c, String mode, @Nullable String caseId, String prompt) {
        c.startActivity(new Intent(c, ChatActivity.class).putExtra(EXTRA_MODE, mode)
                .putExtra(EXTRA_CASE_ID, caseId).putExtra(EXTRA_PROMPT, prompt));
    }

    public static void open(Context c, String chatId) {
        c.startActivity(new Intent(c, ChatActivity.class).putExtra(EXTRA_CHAT_ID, chatId));
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!ensureSession()) return;
        b = ActivityChatBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        applyInsets(b.getRoot());
        setupToolbar(b.toolbar, "", true);

        vm = new ViewModelProvider(this).get(ChatViewModel.class);
        vm.init(getIntent().getStringExtra(EXTRA_CHAT_ID), getIntent().getStringExtra(EXTRA_MODE), getIntent().getStringExtra(EXTRA_CASE_ID));

        adapter = new MessageAdapter(this);
        LinearLayoutManager lm = new LinearLayoutManager(this);
        lm.setStackFromEnd(true);
        b.messages.setLayoutManager(lm);
        b.messages.setAdapter(adapter);
        b.messages.setItemAnimator(null);
        b.messages.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                if (dy < 0 && lm.findFirstVisibleItemPosition() <= 2 && vm.hasOlder()) vm.loadOlder();
            }
        });

        b.toolbar.inflateMenu(R.menu.menu_chat);
        b.toolbar.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == R.id.action_debate_analysis) {
                Dialogs.confirmCost(this, getString(R.string.chat_debate_analysis), Session.get().costOf("DEBATE_ANALYSIS"), vm::debateAnalysis);
            } else if (id == R.id.action_change_mode) {
                pickMode();
            } else if (id == R.id.action_delete_chat) {
                Dialogs.confirmDelete(this, getString(R.string.chat_item), this::deleteChat);
            }
            return true;
        });

        b.send.setOnClickListener(v -> send());
        b.attach.setOnClickListener(v -> pickFile.launch(allowedTypes()));
        b.input.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int c, int d) {}
            @Override public void onTextChanged(CharSequence s, int a, int c, int d) {}
            @Override public void afterTextChanged(Editable s) { updateSendState(); }
        });
        AppConfig cfg = Session.get().config();
        if (cfg != null && !Texts.blank(cfg.aiDisclaimer)) b.disclaimer.setText(cfg.aiDisclaimer);

        vm.messages().observe(this, this::render);
        vm.sending().observe(this, s -> {
            render(vm.messages().getValue());
            updateSendState();
        });
        vm.loading().observe(this, l -> {
            if (Boolean.TRUE.equals(l)) b.state.loading();
            else b.state.hide();
        });
        vm.uploading().observe(this, u -> {
            b.uploadProgress.setVisibility(Boolean.TRUE.equals(u) ? View.VISIBLE : View.GONE);
            b.attach.setEnabled(!Boolean.TRUE.equals(u));
        });
        vm.attachments().observe(this, this::renderAttachments);
        vm.chat().observe(this, c -> updateHeader());
        vm.errors().observe(this, ev -> {
            ApiError e = ev == null ? null : ev.take();
            if (e != null) showError(e);
        });
        vm.loadError().observe(this, ev -> {
            ApiError e = ev == null ? null : ev.take();
            if (e != null) b.state.error(e, vm::load);
        });
        Session.get().bootstrap().observe(this, boot -> updateHeader());

        String prompt = getIntent().getStringExtra(EXTRA_PROMPT);
        if (savedInstanceState == null && !Texts.blank(prompt)) b.input.setText(prompt);
        updateHeader();
        updateSendState();
    }

    private void render(@Nullable List<AiMessage> list) {
        if (list == null) return;
        adapter.submit(list, Boolean.TRUE.equals(vm.sending().getValue()));
        boolean empty = list.isEmpty() && !Boolean.TRUE.equals(vm.loading().getValue());
        b.intro.setVisibility(empty && vm.chatId() == null ? View.VISIBLE : View.GONE);
        if (adapter.getItemCount() > 0) b.messages.scrollToPosition(adapter.getItemCount() - 1);
        invalidateMenuState();
    }

    private void updateHeader() {
        AiModes m = AiModes.of(vm.mode());
        AiChat c = vm.chat().getValue();
        b.toolbar.setTitle(c != null && !Texts.blank(c.title) ? c.title : getString(m.title));
        String cost = m.costLabel(this);
        Wallet w = Session.get().wallet();
        String balance = w != null ? getString(R.string.chat_balance, w.remainingCredits) : null;
        b.toolbar.setSubtitle(Texts.joinNonBlank(" • ", getString(m.title), cost, balance));

        b.introIcon.setImageResource(m.icon);
        m.tone.tile(b.introTile, b.introIcon);
        b.introTitle.setText(m.title);
        b.introDesc.setText(m.desc);
        b.introChips.removeAllViews();
        for (String s : getResources().getStringArray(m.suggestions)) {
            Chip chip = Ui.chip(b.introChips, s, false);
            chip.setOnClickListener(v -> {
                b.input.setText(s);
                b.input.setSelection(s.length());
                b.input.requestFocus();
            });
        }
        boolean needsFile = m == AiModes.DOCUMENT || m == AiModes.COMPARE;
        b.input.setHint(needsFile ? R.string.chat_hint_document : R.string.chat_hint);
        invalidateMenuState();
    }

    private void invalidateMenuState() {
        if (b.toolbar.getMenu().size() == 0) return;
        boolean isNew = vm.chatId() == null;
        b.toolbar.getMenu().findItem(R.id.action_change_mode).setVisible(isNew && vm.messages().getValue() != null && vm.messages().getValue().isEmpty());
        b.toolbar.getMenu().findItem(R.id.action_delete_chat).setVisible(!isNew);
        b.toolbar.getMenu().findItem(R.id.action_debate_analysis).setVisible(!isNew
                && AiModes.DEBATE.code.equals(vm.mode()) && vm.userMessageCount() >= 2);
    }

    private void updateSendState() {
        boolean busy = Boolean.TRUE.equals(vm.sending().getValue());
        boolean hasText = !Texts.blank(b.input.getText());
        List<Attachment> att = vm.attachments().getValue();
        b.send.setEnabled(!busy && (hasText || (att != null && !att.isEmpty())));
    }

    private void send() {
        String text = Texts.trim(b.input.getText());
        List<Attachment> att = vm.attachments().getValue();
        if (text.isEmpty() && (att == null || att.isEmpty())) return;
        AiModes m = AiModes.of(vm.mode());
        if (!m.enabled()) {
            snack(getString(R.string.error_feature_disabled));
            return;
        }
        int cost = Session.get().costOf(m.code);
        Wallet w = Session.get().wallet();
        boolean free = w != null && w.freeServiceUses > 0;
        if (!confirmedCost && !free && cost >= 3) {
            Dialogs.confirmCost(this, getString(m.title), cost, () -> {
                confirmedCost = true;
                doSend(text);
            });
            return;
        }
        doSend(text);
    }

    private void doSend(String text) {
        vm.send(text.isEmpty() ? getString(R.string.chat_analyze_attachments) : text);
        b.input.setText("");
        Ui.hideKeyboard(b.input);
    }

    private void renderAttachments(@Nullable List<Attachment> list) {
        b.attachmentChips.removeAllViews();
        boolean any = list != null && !list.isEmpty();
        b.attachmentsScroll.setVisibility(any ? View.VISIBLE : View.GONE);
        if (any) {
            for (Attachment a : list) {
                Chip c = Ui.chip(b.attachmentChips, a.name, false);
                c.setChipIconResource(R.drawable.ic_attach);
                c.setChipIconVisible(true);
                c.setCloseIconVisible(true);
                c.setOnCloseIconClickListener(v -> vm.removeAttachment(a));
            }
        }
        updateSendState();
    }

    private String[] allowedTypes() {
        AppConfig cfg = Session.get().config();
        if (cfg != null && cfg.allowedUploadTypes != null && !cfg.allowedUploadTypes.isEmpty()) {
            return cfg.allowedUploadTypes.toArray(new String[0]);
        }
        return new String[]{"application/pdf", "image/*", "text/plain",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "application/msword"};
    }

    private void onFilePicked(@Nullable Uri uri) {
        if (uri == null) return;
        UriRequestBody.FileInfo info = UriRequestBody.inspect(this, uri);
        AppConfig cfg = Session.get().config();
        int maxMb = cfg != null && cfg.maxUploadMb != null ? cfg.maxUploadMb : BuildConfig.MAX_UPLOAD_MB;
        if (info.size > (long) maxMb * 1024 * 1024) {
            snack(getString(R.string.error_file_too_large_mb, maxMb));
            return;
        }
        vm.upload(info);
    }

    private void pickMode() {
        AiModes[] modes = AiModes.values();
        String[] labels = new String[modes.length];
        for (int i = 0; i < modes.length; i++) labels[i] = getString(modes[i].title);
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.chat_change_mode)
                .setItems(labels, (d, which) -> {
                    vm.setMode(modes[which].code);
                    confirmedCost = false;
                    updateHeader();
                })
                .show();
    }

    private void deleteChat() {
        String id = vm.chatId();
        if (id == null) return;
        call(ApiClient.api().deleteChat(id), ok -> finish());
    }

    // ---- MessageAdapter.Listener ----
    @Override
    public void onCitation(Citation c) {
        Router.openCitation(this, c);
    }

    @Override
    public void onRetry(AiMessage m) {
        vm.retry(m);
    }

    @Override
    public void onCopy(AiMessage m) {
        Intents.copy(this, m.content == null ? "" : m.content);
    }

    @Override
    public void onShare(AiMessage m) {
        Intents.shareText(this, getString(R.string.app_name), (m.content == null ? "" : m.content)
                + "\n\n— " + getString(R.string.app_name));
    }
}
