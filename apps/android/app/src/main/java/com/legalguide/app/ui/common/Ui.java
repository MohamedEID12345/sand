package com.legalguide.app.ui.common;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.progressindicator.CircularProgressIndicatorSpec;
import com.google.android.material.progressindicator.IndeterminateDrawable;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.legalguide.app.R;
import com.legalguide.app.core.util.Texts;
import com.legalguide.app.data.model.KeyValue;
import com.legalguide.app.data.model.Option;
import com.legalguide.app.databinding.ItemKvBinding;

import java.util.List;

public final class Ui {
    private Ui() {}

    public static int dp(Context c, float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, c.getResources().getDisplayMetrics()));
    }

    public static void show(View v, boolean visible) {
        v.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    /** Sets text and hides the view when blank. */
    public static void text(TextView tv, @Nullable CharSequence s) {
        tv.setText(s);
        show(tv, !Texts.blank(s));
    }

    public static int attrColor(Context c, int attr) {
        TypedValue tv = new TypedValue();
        c.getTheme().resolveAttribute(attr, tv, true);
        return tv.data;
    }

    /** Replaces the button icon with a spinner and disables it while loading. */
    public static void loading(MaterialButton btn, boolean loading) {
        if (loading) {
            if (btn.getTag(R.id.tag_button_icon) == null) btn.setTag(R.id.tag_button_icon, btn.getIcon() == null ? "none" : btn.getIcon());
            CircularProgressIndicatorSpec spec = new CircularProgressIndicatorSpec(btn.getContext(), null, 0,
                    com.google.android.material.R.style.Widget_Material3_CircularProgressIndicator_ExtraSmall);
            spec.indicatorColors = new int[]{btn.getCurrentTextColor()};
            Drawable d = IndeterminateDrawable.createCircularDrawable(btn.getContext(), spec);
            btn.setIcon(d);
            btn.setIconTint(ColorStateList.valueOf(btn.getCurrentTextColor()));
            btn.setEnabled(false);
        } else {
            Object prev = btn.getTag(R.id.tag_button_icon);
            btn.setIcon(prev instanceof Drawable ? (Drawable) prev : null);
            btn.setTag(R.id.tag_button_icon, null);
            btn.setEnabled(true);
        }
    }

    public static void hideKeyboard(View v) {
        InputMethodManager imm = (InputMethodManager) v.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
    }

    @Nullable
    public static FragmentActivity activity(Context c) {
        while (c instanceof ContextWrapper) {
            if (c instanceof FragmentActivity) return (FragmentActivity) c;
            c = ((ContextWrapper) c).getBaseContext();
        }
        return null;
    }

    /** Appends a label/value row into a container. Returns the row's value view. */
    public static TextView kv(ViewGroup container, CharSequence label, @Nullable CharSequence value) {
        ItemKvBinding b = ItemKvBinding.inflate(LayoutInflater.from(container.getContext()), container, true);
        b.kvLabel.setText(label);
        b.kvValue.setText(Texts.blank(value) ? "—" : value);
        return b.kvValue;
    }

    public static void kvList(ViewGroup container, @Nullable List<KeyValue> rows) {
        if (rows == null) return;
        for (KeyValue kv : rows) kv(container, kv.label, kv.value);
    }

    /** Fills an exposed-dropdown with options; returns the adapter. */
    public static ArrayAdapter<Option> dropdown(MaterialAutoCompleteTextView view, List<Option> options) {
        ArrayAdapter<Option> a = new ArrayAdapter<>(view.getContext(), R.layout.item_dropdown, options);
        view.setAdapter(a);
        return a;
    }

    public static void selectOption(MaterialAutoCompleteTextView view, List<Option> options, @Nullable String value) {
        if (value == null) return;
        for (Option o : options) {
            if (value.equals(o.value)) {
                view.setText(o.label, false);
                return;
            }
        }
    }

    @Nullable
    public static String optionValue(List<Option> options, @Nullable CharSequence label) {
        if (label == null) return null;
        String l = label.toString();
        for (Option o : options) if (l.equals(o.label)) return o.value;
        return null;
    }

    public static Chip chip(ChipGroup group, CharSequence text, boolean checkable) {
        Chip c = (Chip) LayoutInflater.from(group.getContext()).inflate(
                checkable ? R.layout.view_chip_filter : R.layout.view_chip_assist, group, false);
        c.setId(View.generateViewId());
        c.setText(text);
        group.addView(c);
        return c;
    }
}
