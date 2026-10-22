package com.legalguide.app.ui.ai;

import android.app.Application;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.legalguide.app.core.net.ApiClient;
import com.legalguide.app.core.net.ApiError;
import com.legalguide.app.core.net.Calls;
import com.legalguide.app.core.net.UriRequestBody;
import com.legalguide.app.core.session.Session;
import com.legalguide.app.core.util.Json;
import com.legalguide.app.data.model.AiChat;
import com.legalguide.app.data.model.AiMessage;
import com.legalguide.app.data.model.Attachment;
import com.legalguide.app.data.model.SendMessageResponse;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import retrofit2.Call;

/** Chat state survives rotation; network calls are cancelled when the screen is finished. */
public class ChatViewModel extends AndroidViewModel {

    /** Single-shot wrapper for one-off UI events. */
    public static final class Event<T> {
        private final T value;
        private boolean handled;

        Event(T value) { this.value = value; }

        @Nullable
        public T take() {
            if (handled) return null;
            handled = true;
            return value;
        }
    }

    private final MutableLiveData<List<AiMessage>> messages = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<Boolean> sending = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> loading = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> uploading = new MutableLiveData<>(false);
    private final MutableLiveData<List<Attachment>> attachments = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<AiChat> chat = new MutableLiveData<>();
    private final MutableLiveData<Event<ApiError>> errors = new MutableLiveData<>();
    private final MutableLiveData<Event<ApiError>> loadError = new MutableLiveData<>();

    @Nullable private String chatId;
    private String mode = AiModes.QUESTION.code;
    @Nullable private String caseId;
    @Nullable private String olderCursor;
    private boolean initialized;
    private boolean loadingOlder;
    private final List<Call<?>> calls = new ArrayList<>();

    public ChatViewModel(@NonNull Application app) {
        super(app);
    }

    public LiveData<List<AiMessage>> messages() { return messages; }
    public LiveData<Boolean> sending() { return sending; }
    public LiveData<Boolean> loading() { return loading; }
    public LiveData<Boolean> uploading() { return uploading; }
    public LiveData<List<Attachment>> attachments() { return attachments; }
    public LiveData<AiChat> chat() { return chat; }
    public LiveData<Event<ApiError>> errors() { return errors; }
    public LiveData<Event<ApiError>> loadError() { return loadError; }

    @Nullable public String chatId() { return chatId; }
    public String mode() { return mode; }
    @Nullable public String caseId() { return caseId; }
    public boolean hasOlder() { return olderCursor != null; }

    public void init(@Nullable String chatId, @Nullable String mode, @Nullable String caseId) {
        if (initialized) return;
        initialized = true;
        this.chatId = chatId;
        if (mode != null) this.mode = mode;
        this.caseId = caseId;
        if (chatId != null) load();
    }

    public void setMode(String mode) {
        if (chatId == null) this.mode = mode;
    }

    public void load() {
        if (chatId == null) return;
        loading.setValue(true);
        track(Calls.enqueue(null, ApiClient.api().chat(chatId), c -> {
            if (c != null) {
                chat.setValue(c);
                if (c.mode != null) mode = c.mode;
                caseId = c.caseId;
            }
        }, null));
        track(Calls.enqueue(null, ApiClient.api().messages(chatId, null), page -> {
            loading.setValue(false);
            List<AiMessage> list = page != null && page.items != null ? new ArrayList<>(page.items) : new ArrayList<>();
            olderCursor = page != null ? page.nextCursor : null;
            messages.setValue(list);
        }, e -> {
            loading.setValue(false);
            loadError.setValue(new Event<>(e));
        }));
    }

    public void loadOlder() {
        if (chatId == null || olderCursor == null || loadingOlder) return;
        loadingOlder = true;
        track(Calls.enqueue(null, ApiClient.api().messages(chatId, olderCursor), page -> {
            loadingOlder = false;
            if (page == null || page.items == null) return;
            olderCursor = page.nextCursor;
            List<AiMessage> list = new ArrayList<>(page.items);
            list.addAll(current());
            messages.setValue(list);
        }, e -> loadingOlder = false));
    }

    public void send(String text) {
        if (Boolean.TRUE.equals(sending.getValue())) return;
        AiMessage local = new AiMessage();
        local.id = "local-" + UUID.randomUUID();
        local.role = "user";
        local.content = text;
        local.createdAt = Instant.now().toString();
        local.attachments = new ArrayList<>(currentAttachments());
        local.localState = AiMessage.STATE_PENDING;
        List<AiMessage> list = current();
        list.add(local);
        messages.setValue(list);
        attachments.setValue(new ArrayList<>());
        deliver(local);
    }

    public void retry(AiMessage failed) {
        if (Boolean.TRUE.equals(sending.getValue())) return;
        failed.localState = AiMessage.STATE_PENDING;
        messages.setValue(current());
        deliver(failed);
    }

    private void deliver(AiMessage local) {
        sending.setValue(true);
        if (chatId == null) {
            track(Calls.enqueue(null, ApiClient.api().createChat(Json.map("mode", mode, "caseId", caseId)), c -> {
                if (c == null || c.id == null) {
                    fail(local, ApiError.parse(500, null));
                    return;
                }
                chatId = c.id;
                chat.setValue(c);
                post(local);
            }, e -> fail(local, e)));
        } else {
            post(local);
        }
    }

    private void post(AiMessage local) {
        List<String> ids = new ArrayList<>();
        if (local.attachments != null) for (Attachment a : local.attachments) ids.add(a.id);
        Map<String, Object> body = Json.map("content", local.content, "mode", mode, "caseId", caseId,
                "attachmentIds", ids.isEmpty() ? null : ids, "clientMessageId", local.id);
        track(Calls.enqueue(null, ApiClient.api().sendMessage(chatId, body), res -> onReply(local, res), e -> fail(local, e)));
    }

    private void onReply(AiMessage local, @Nullable SendMessageResponse res) {
        sending.setValue(false);
        List<AiMessage> list = current();
        int idx = list.indexOf(local);
        if (res != null && res.userMessage != null && idx >= 0) {
            list.set(idx, res.userMessage);
        } else {
            local.localState = AiMessage.STATE_SENT;
        }
        if (res != null && res.message != null) {
            if (res.charge != null) {
                res.message.creditsCharged = res.charge.credits;
                res.message.usedFreeUse = res.charge.usedFreeUse;
            }
            list.add(res.message);
        }
        if (res != null) {
            Session.get().updateWallet(res.wallet);
            if (res.chat != null) chat.setValue(res.chat);
        }
        messages.setValue(list);
    }

    private void fail(AiMessage local, ApiError e) {
        sending.setValue(false);
        local.localState = AiMessage.STATE_FAILED;
        messages.setValue(current());
        errors.setValue(new Event<>(e));
    }

    public void debateAnalysis() {
        if (chatId == null || Boolean.TRUE.equals(sending.getValue())) return;
        sending.setValue(true);
        track(Calls.enqueue(null, ApiClient.api().debateAnalysis(chatId), res -> {
            sending.setValue(false);
            if (res == null) return;
            List<AiMessage> list = current();
            if (res.message != null) {
                if (res.charge != null) {
                    res.message.creditsCharged = res.charge.credits;
                    res.message.usedFreeUse = res.charge.usedFreeUse;
                }
                list.add(res.message);
            }
            Session.get().updateWallet(res.wallet);
            messages.setValue(list);
        }, e -> {
            sending.setValue(false);
            errors.setValue(new Event<>(e));
        }));
    }

    public void upload(UriRequestBody.FileInfo info) {
        uploading.setValue(true);
        track(Calls.enqueue(null, ApiClient.api().aiUpload(UriRequestBody.part(getApplication(), "file", info)), a -> {
            uploading.setValue(false);
            if (a == null) return;
            List<Attachment> list = currentAttachments();
            list.add(a);
            attachments.setValue(list);
        }, e -> {
            uploading.setValue(false);
            errors.setValue(new Event<>(e));
        }));
    }

    public void removeAttachment(Attachment a) {
        List<Attachment> list = currentAttachments();
        list.remove(a);
        attachments.setValue(list);
    }

    public int userMessageCount() {
        int n = 0;
        for (AiMessage m : current()) if (m.isUser()) n++;
        return n;
    }

    private List<AiMessage> current() {
        List<AiMessage> v = messages.getValue();
        return v == null ? new ArrayList<>() : new ArrayList<>(v);
    }

    private List<Attachment> currentAttachments() {
        List<Attachment> v = attachments.getValue();
        return v == null ? new ArrayList<>() : new ArrayList<>(v);
    }

    private void track(Call<?> c) {
        calls.add(c);
        if (calls.size() > 32) calls.remove(0);
    }

    @Override
    protected void onCleared() {
        for (Call<?> c : calls) c.cancel();
        calls.clear();
    }
}
