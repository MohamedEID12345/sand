package com.legalguide.app.ui.cases;

import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.legalguide.app.BuildConfig;
import com.legalguide.app.R;
import com.legalguide.app.core.net.ApiClient;
import com.legalguide.app.core.net.UriRequestBody;
import com.legalguide.app.core.session.Session;
import com.legalguide.app.core.util.Dates;
import com.legalguide.app.core.util.Downloads;
import com.legalguide.app.core.util.Texts;
import com.legalguide.app.data.model.AppConfig;
import com.legalguide.app.data.model.CaseDocument;
import com.legalguide.app.data.model.CaseMeta;
import com.legalguide.app.data.model.Option;
import com.legalguide.app.data.model.Page;
import com.legalguide.app.databinding.DialogUploadDocumentBinding;
import com.legalguide.app.ui.ai.AiModes;
import com.legalguide.app.ui.ai.ChatActivity;
import com.legalguide.app.ui.common.Dialogs;
import com.legalguide.app.ui.common.ErrorUi;
import com.legalguide.app.ui.common.ListController;
import com.legalguide.app.ui.common.RowAdapter;
import com.legalguide.app.ui.common.Tone;
import com.legalguide.app.ui.common.Ui;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;

public class CaseDocumentsFragment extends CaseTabListFragment<CaseDocument> {
    private ActivityResultLauncher<String[]> picker;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        picker = registerForActivityResult(new ActivityResultContracts.OpenDocument(), this::onPicked);
    }

    @Override
    protected Call<Page<CaseDocument>> load(String caseId, @Nullable String cursor) {
        return ApiClient.api().caseDocuments(caseId, cursor);
    }

    @Override
    protected void bind(RowAdapter.Row row, CaseDocument d) {
        boolean image = d.mimeType != null && d.mimeType.startsWith("image/");
        row.icon(image ? R.drawable.ic_image : R.drawable.ic_document, image ? Tone.INFO : Tone.PRIMARY)
                .title(Texts.blank(d.title) ? d.fileName : d.title)
                .subtitle(Texts.joinNonBlank(" • ", d.docTypeLabel, Texts.fileSize(d.sizeBytes),
                        d.pages != null ? getString(R.string.doc_pages, d.pages) : null))
                .meta(Texts.joinNonBlank(" • ", Dates.day(d.createdAt), d.uploadedBy))
                .badge(ocrLabel(d.ocrStatus), Tone.forStatus(d.ocrStatus));
    }

    @Nullable
    private String ocrLabel(@Nullable String s) {
        if ("PENDING".equals(s) || "PROCESSING".equals(s)) return getString(R.string.doc_ocr_processing);
        if ("FAILED".equals(s)) return getString(R.string.doc_ocr_failed);
        return null;
    }

    @Override
    protected void onItemClick(CaseDocument d) {
        List<String> actions = new ArrayList<>();
        actions.add(getString(R.string.action_download));
        if (Session.get().feature("ai")) actions.add(getString(R.string.doc_ask_ai));
        boolean canDelete = Session.get().can("documents.delete");
        if (canDelete) actions.add(getString(R.string.action_delete));
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(Texts.blank(d.title) ? d.fileName : d.title)
                .setItems(actions.toArray(new String[0]), (dlg, which) -> {
                    String a = actions.get(which);
                    if (a.equals(getString(R.string.action_download))) {
                        call(ApiClient.api().documentDownload(d.id), link -> Downloads.enqueue(requireContext(), link, d.fileName));
                    } else if (a.equals(getString(R.string.doc_ask_ai))) {
                        ChatActivity.startWithPrompt(requireContext(), AiModes.DOCUMENT.code, vm.caseId(),
                                getString(R.string.doc_ask_ai_prompt, Texts.blank(d.title) ? d.fileName : d.title));
                    } else {
                        Dialogs.confirmDelete(requireContext(), getString(R.string.kind_document), () ->
                                call(ApiClient.api().deleteDocument(d.id), ok -> vm.notifyChanged()));
                    }
                })
                .show();
    }

    @Override
    protected void configure(ListController<CaseDocument> c) {
        c.empty(R.drawable.ic_document, getString(R.string.docs_empty_title), getString(R.string.docs_empty_message));
    }

    @Override public int fabText() { return R.string.doc_upload; }
    @Override public int fabIcon() { return R.drawable.ic_upload; }
    @Override public boolean fabVisible() { return Session.get().can("documents.create"); }

    @Override
    public void onFab() {
        picker.launch(new String[]{"application/pdf", "image/*", "application/msword",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "text/plain"});
    }

    private void onPicked(@Nullable Uri uri) {
        if (uri == null) return;
        UriRequestBody.FileInfo info = UriRequestBody.inspect(requireContext(), uri);
        AppConfig cfg = Session.get().config();
        int maxMb = cfg != null && cfg.maxUploadMb != null ? cfg.maxUploadMb : BuildConfig.MAX_UPLOAD_MB;
        if (info.size > (long) maxMb * 1024 * 1024) {
            ErrorUi.snack(getActivity(), getString(R.string.error_file_too_large_mb, maxMb));
            return;
        }
        DialogUploadDocumentBinding d = DialogUploadDocumentBinding.inflate(LayoutInflater.from(requireContext()));
        String base = info.name.contains(".") ? info.name.substring(0, info.name.lastIndexOf('.')) : info.name;
        d.title.setText(base);
        d.fileName.setText(Texts.joinNonBlank(" • ", info.name, Texts.fileSize(info.size)));
        CaseMeta meta = Session.get().caseMeta();
        List<Option> types = meta != null && meta.documentTypes != null ? meta.documentTypes : new ArrayList<>();
        Ui.dropdown(d.docType, types);
        Ui.show(d.docTypeLayout, !types.isEmpty());
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.doc_upload)
                .setView(d.getRoot())
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.action_upload, (dlg, w) -> upload(info, Texts.trim(d.title.getText()),
                        Ui.optionValue(types, d.docType.getText())))
                .show();
    }

    private void upload(UriRequestBody.FileInfo info, String title, @Nullable String type) {
        if (b == null) return;
        b.uploadProgress.setVisibility(View.VISIBLE);
        call(ApiClient.api().uploadDocument(vm.caseId(), UriRequestBody.part(requireContext(), "file", info),
                UriRequestBody.text(title.isEmpty() ? info.name : title), UriRequestBody.text(type)), doc -> {
            if (b != null) b.uploadProgress.setVisibility(View.GONE);
            ErrorUi.snack(getActivity(), getString(R.string.doc_uploaded));
            vm.notifyChanged();
        }, e -> {
            if (b != null) b.uploadProgress.setVisibility(View.GONE);
            showError(e);
        });
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
    }
}
