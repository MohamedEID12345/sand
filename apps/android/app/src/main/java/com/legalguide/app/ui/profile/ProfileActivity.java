package com.legalguide.app.ui.profile;

import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.Nullable;

import com.legalguide.app.R;
import com.legalguide.app.core.net.ApiClient;
import com.legalguide.app.core.session.Session;
import com.legalguide.app.core.util.FieldValidator;
import com.legalguide.app.core.util.Json;
import com.legalguide.app.core.util.Texts;
import com.legalguide.app.data.model.AppConfig;
import com.legalguide.app.data.model.Option;
import com.legalguide.app.data.model.User;
import com.legalguide.app.databinding.ActivityProfileBinding;
import com.legalguide.app.ui.common.BaseActivity;
import com.legalguide.app.ui.common.Tone;
import com.legalguide.app.ui.common.Ui;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ProfileActivity extends BaseActivity {
    private ActivityProfileBinding b;
    private final List<Option> governorates = new ArrayList<>();
    private final List<Option> degrees = new ArrayList<>();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!ensureSession()) return;
        b = ActivityProfileBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        applyInsets(b.getRoot());
        setupToolbar(b.appBar.toolbar, R.string.profile_title, true);

        AppConfig cfg = Session.get().config();
        List<String> govs = cfg != null && cfg.governorates != null && !cfg.governorates.isEmpty()
                ? cfg.governorates : Arrays.asList(getResources().getStringArray(R.array.governorates));
        for (String g : govs) governorates.add(new Option(g, g));
        Ui.dropdown(b.governorate, governorates);
        if (cfg != null && cfg.degrees != null && !cfg.degrees.isEmpty()) {
            degrees.addAll(cfg.degrees);
        } else {
            String[] labels = getResources().getStringArray(R.array.degrees);
            String[] values = getResources().getStringArray(R.array.degree_values);
            for (int i = 0; i < labels.length; i++) degrees.add(new Option(values[i], labels[i]));
        }
        Ui.dropdown(b.degree, degrees);

        b.changePassword.setOnClickListener(v -> startActivity(new Intent(this, ChangePasswordActivity.class)));
        b.save.setOnClickListener(v -> save());
        fill(Session.get().user());
        call(ApiClient.api().bootstrap(), boot -> {
            Session.get().setBootstrap(boot);
            if (boot != null) fill(boot.user);
        }, e -> { });
    }

    private void fill(@Nullable User u) {
        if (u == null) return;
        b.avatar.setText(Texts.initials(u.name));
        b.headerName.setText(u.name);
        b.headerEmail.setText(u.email);
        String role = Texts.joinNonBlank(" • ", u.officeName, u.officeRoleLabel);
        Ui.text(b.headerRole, role);
        if (!Texts.blank(role)) Tone.PRIMARY.badge(b.headerRole);
        b.name.setText(u.name);
        b.email.setText(u.email);
        b.phone.setText(u.phone);
        Ui.selectOption(b.governorate, governorates, u.governorate);
        if (u.professional != null) {
            b.syndicate.setText(u.professional.syndicateNumber);
            Ui.selectOption(b.degree, degrees, u.professional.degree);
            b.specialization.setText(u.professional.specialization);
        }
    }

    private void save() {
        b.nameLayout.setError(null);
        b.phoneLayout.setError(null);
        String name = Texts.trim(b.name.getText());
        String phone = Texts.asciiDigits(Texts.trim(b.phone.getText()));
        boolean ok = true;
        if (name.length() < 3) { b.nameLayout.setError(getString(R.string.error_name)); ok = false; }
        if (!FieldValidator.isEgyptianMobile(phone)) { b.phoneLayout.setError(getString(R.string.error_phone)); ok = false; }
        if (!ok) return;
        Ui.loading(b.save, true);
        call(ApiClient.api().updateProfile(Json.map("name", name, "phone", phone,
                "governorate", Ui.optionValue(governorates, b.governorate.getText()),
                "professional", Json.map("syndicateNumber", Texts.nullIfBlank(Texts.asciiDigits(Texts.trim(b.syndicate.getText()))),
                        "degree", Ui.optionValue(degrees, b.degree.getText()),
                        "specialization", Texts.nullIfBlank(b.specialization.getText())))), u -> {
            Ui.loading(b.save, false);
            Session.get().updateUser(u);
            fill(u);
            toast(R.string.saved);
        }, e -> {
            Ui.loading(b.save, false);
            if (e.fieldErrors.containsKey("phone")) b.phoneLayout.setError(e.fieldErrors.get("phone"));
            else showError(e);
        });
    }
}
