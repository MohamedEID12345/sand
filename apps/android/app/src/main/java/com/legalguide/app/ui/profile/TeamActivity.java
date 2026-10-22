package com.legalguide.app.ui.profile;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.Nullable;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.legalguide.app.R;
import com.legalguide.app.core.net.ApiClient;
import com.legalguide.app.core.session.Session;
import com.legalguide.app.core.util.FieldValidator;
import com.legalguide.app.core.util.Json;
import com.legalguide.app.core.util.Texts;
import com.legalguide.app.data.model.Office;
import com.legalguide.app.data.model.OfficeMember;
import com.legalguide.app.data.model.Option;
import com.legalguide.app.data.model.Page;
import com.legalguide.app.databinding.DialogInviteBinding;
import com.legalguide.app.databinding.ViewTeamHeaderBinding;
import com.legalguide.app.ui.common.Dialogs;
import com.legalguide.app.ui.common.ListController;
import com.legalguide.app.ui.common.RowAdapter;
import com.legalguide.app.ui.common.SimpleListActivity;
import com.legalguide.app.ui.common.Tone;
import com.legalguide.app.ui.common.Ui;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;

/** Office members & roles (OWNER, SENIOR_LAWYER, LAWYER, JUNIOR_LAWYER, SECRETARY, ACCOUNTANT). */
public class TeamActivity extends SimpleListActivity<OfficeMember> {
    private final List<Option> roles = new ArrayList<>();
    private ViewTeamHeaderBinding h;

    @Override
    protected CharSequence title() {
        return getString(R.string.team_title);
    }

    @Nullable
    @Override
    protected View createHeader(ViewGroup parent) {
        h = ViewTeamHeaderBinding.inflate(LayoutInflater.from(this), parent, false);
        return h.getRoot();
    }

    /** GET /office returns the members inline; wrap them as a single page. */
    @Override
    protected Call<Page<OfficeMember>> load(@Nullable String cursor) {
        return new OfficePageCall(ApiClient.api().office(), this::onOffice);
    }

    private void onOffice(Office o) {
        roles.clear();
        if (o.roles != null) roles.addAll(o.roles);
        if (h != null) {
            h.officeName.setText(o.name);
            h.seats.setText(o.seatsMax != null ? getString(R.string.team_seats, o.seatsUsed, o.seatsMax)
                    : getString(R.string.team_seats_unlimited, o.seatsUsed));
        }
    }

    @Override
    protected void bind(RowAdapter.Row row, OfficeMember m) {
        row.avatar(m.name).title(m.name).subtitle(m.email)
                .badge(m.roleLabel, "OWNER".equals(m.role) ? Tone.GOLD : Tone.PRIMARY)
                .meta("INVITED".equals(m.status) ? getString(R.string.team_invited) : null)
                .chevron(Session.get().can("office.manage") && !"OWNER".equals(m.role));
    }

    @Override
    protected void onItemClick(OfficeMember m) {
        if (!Session.get().can("office.manage") || "OWNER".equals(m.role)) return;
        String[] actions = {getString(R.string.team_change_role), getString(R.string.team_remove)};
        new MaterialAlertDialogBuilder(this).setTitle(m.name).setItems(actions, (d, which) -> {
            if (which == 0) changeRole(m);
            else Dialogs.confirm(this, getString(R.string.team_remove), getString(R.string.team_remove_confirm, m.name),
                    R.string.team_remove, () -> call(ApiClient.api().removeMember(m.id), ok -> reload()));
        }).show();
    }

    private void changeRole(OfficeMember m) {
        List<Option> assignable = assignableRoles();
        String[] labels = new String[assignable.size()];
        for (int i = 0; i < labels.length; i++) labels[i] = assignable.get(i).label;
        new MaterialAlertDialogBuilder(this).setTitle(R.string.team_change_role).setItems(labels, (d, which) ->
                call(ApiClient.api().updateMember(m.id, Json.map("role", assignable.get(which).value)), r -> reload())).show();
    }

    private List<Option> assignableRoles() {
        List<Option> out = new ArrayList<>();
        for (Option o : roles) if (!"OWNER".equals(o.value)) out.add(o);
        return out;
    }

    @Override
    protected void configure(ListController<OfficeMember> c) {
        c.empty(R.drawable.ic_team, getString(R.string.team_empty), null);
    }

    @Override
    protected void setupFab(ExtendedFloatingActionButton fab) {
        if (!Session.get().can("office.manage")) return;
        fab.setVisibility(View.VISIBLE);
        fab.setText(R.string.team_invite);
        fab.setIconResource(R.drawable.ic_add);
        fab.setOnClickListener(v -> invite());
    }

    private void invite() {
        DialogInviteBinding d = DialogInviteBinding.inflate(LayoutInflater.from(this));
        List<Option> assignable = assignableRoles();
        Ui.dropdown(d.role, assignable);
        if (!assignable.isEmpty()) d.role.setText(assignable.get(assignable.size() > 2 ? 2 : 0).label, false);
        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.team_invite)
                .setView(d.getRoot())
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.team_send_invite, null)
                .show();
        dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String email = Texts.trim(d.email.getText());
            String role = Ui.optionValue(assignable, d.role.getText());
            if (!FieldValidator.isEmail(email)) {
                d.emailLayout.setError(getString(R.string.error_email));
                return;
            }
            if (role == null) {
                d.roleLayout.setError(getString(R.string.error_required));
                return;
            }
            call(ApiClient.api().invite(Json.map("email", email, "role", role)), ok -> {
                dialog.dismiss();
                snack(getString(R.string.team_invite_sent));
                reload();
            });
        });
    }
}
