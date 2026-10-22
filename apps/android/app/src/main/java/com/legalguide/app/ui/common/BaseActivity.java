package com.legalguide.app.ui.common;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.appbar.MaterialToolbar;
import com.legalguide.app.R;
import com.legalguide.app.core.net.ApiError;
import com.legalguide.app.core.net.Calls;
import com.legalguide.app.core.session.Session;
import com.legalguide.app.ui.auth.LoginActivity;

import retrofit2.Call;

/** Edge-to-edge, auth guard, toolbar and lifecycle-safe API calls for every screen. */
public abstract class BaseActivity extends AppCompatActivity {
    private long createdAt;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);
        createdAt = System.currentTimeMillis();
        if (requiresAuth()) {
            Session.get().logoutEvents().observe(this, t -> {
                if (t != null && t > createdAt) goToLogin();
            });
        }
    }

    /** Returns false if the screen cannot be shown (not logged in) — callers must return early. */
    protected boolean ensureSession() {
        if (requiresAuth() && !Session.get().isLoggedIn()) {
            goToLogin();
            return false;
        }
        return true;
    }

    protected boolean requiresAuth() {
        return true;
    }

    protected void goToLogin() {
        startActivity(new Intent(this, LoginActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        finish();
    }

    /** Pads the root for status/navigation bars and the keyboard. */
    protected void applyInsets(View root) {
        applyInsets(root, true, true);
    }

    protected void applyInsets(View root, boolean top, boolean bottom) {
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
            v.setPadding(bars.left, top ? bars.top : 0, bars.right, bottom ? Math.max(bars.bottom, ime.bottom) : 0);
            return bottom ? WindowInsetsCompat.CONSUMED : insets;
        });
    }

    protected void setupToolbar(MaterialToolbar tb, CharSequence title, boolean back) {
        tb.setTitle(title);
        if (back) {
            tb.setNavigationIcon(R.drawable.ic_arrow_back);
            tb.setNavigationContentDescription(R.string.action_back);
            tb.setNavigationOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());
        }
    }

    protected void setupToolbar(MaterialToolbar tb, @StringRes int title, boolean back) {
        setupToolbar(tb, getString(title), back);
    }

    protected <T> Call<T> call(Call<T> c, Calls.Success<T> ok) {
        return Calls.enqueue(this, c, ok, this::showError);
    }

    protected <T> Call<T> call(Call<T> c, Calls.Success<T> ok, Calls.Failure fail) {
        return Calls.enqueue(this, c, ok, fail);
    }

    public void showError(ApiError e) {
        ErrorUi.show(this, e);
    }

    public void snack(CharSequence msg) {
        ErrorUi.snack(this, msg);
    }

    public void toast(@StringRes int res) {
        Toast.makeText(this, res, Toast.LENGTH_SHORT).show();
    }
}
