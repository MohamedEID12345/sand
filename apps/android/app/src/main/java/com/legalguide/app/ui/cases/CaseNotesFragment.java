package com.legalguide.app.ui.cases;

import android.view.LayoutInflater;

import androidx.annotation.Nullable;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.legalguide.app.R;
import com.legalguide.app.core.net.ApiClient;
import com.legalguide.app.core.util.Dates;
import com.legalguide.app.core.util.Json;
import com.legalguide.app.core.util.Texts;
import com.legalguide.app.data.model.CaseNote;
import com.legalguide.app.data.model.Page;
import com.legalguide.app.databinding.DialogInputBinding;
import com.legalguide.app.ui.common.Dialogs;
import com.legalguide.app.ui.common.ListController;
import com.legalguide.app.ui.common.RowAdapter;
import com.legalguide.app.ui.common.Tone;

import retrofit2.Call;

public class CaseNotesFragment extends CaseTabListFragment<CaseNote> {

    @Override
    protected Call<Page<CaseNote>> load(String caseId, @Nullable String cursor) {
        return ApiClient.api().caseNotes(caseId, cursor);
    }

    @Override
    protected void bind(RowAdapter.Row row, CaseNote n) {
        row.icon(R.drawable.ic_notes, Tone.GOLD).title(n.body)
                .meta(Texts.joinNonBlank(" • ", n.authorName, Dates.relative(n.createdAt)))
                .chevron(false);
        row.b.rowTitle.setMaxLines(6);
    }

    @Override
    protected void onItemClick(CaseNote n) {
        Dialogs.info(requireContext(), getString(R.string.note_title), n.body);
    }

    @Override
    protected void onItemLongClick(CaseNote n) {
        Dialogs.confirmDelete(requireContext(), getString(R.string.kind_note), () ->
                call(ApiClient.api().deleteNote(n.id), ok -> controller.refresh()));
    }

    @Override
    protected void configure(ListController<CaseNote> c) {
        c.empty(R.drawable.ic_notes, getString(R.string.notes_empty_title), getString(R.string.notes_empty_message));
    }

    @Override public int fabText() { return R.string.note_new; }
    @Override public int fabIcon() { return R.drawable.ic_add; }

    @Override
    public void onFab() {
        DialogInputBinding d = DialogInputBinding.inflate(LayoutInflater.from(requireContext()));
        d.inputLayout.setHint(getString(R.string.note_hint));
        d.input.setMinLines(4);
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.note_new)
                .setView(d.getRoot())
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.action_save, (dlg, w) -> {
                    String body = Texts.trim(d.input.getText());
                    if (body.isEmpty()) return;
                    call(ApiClient.api().addNote(vm.caseId(), Json.map("body", body, "visibility", "PRIVATE")),
                            note -> controller.refresh());
                })
                .show();
    }
}
