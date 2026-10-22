package com.legalguide.app.ui.common;

import android.content.Context;
import android.text.InputFilter;
import android.text.InputType;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;

import com.google.android.material.chip.Chip;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.legalguide.app.R;
import com.legalguide.app.core.util.Dates;
import com.legalguide.app.core.util.FieldValidator;
import com.legalguide.app.core.util.Texts;
import com.legalguide.app.data.model.FormField;
import com.legalguide.app.data.model.Option;
import com.legalguide.app.databinding.FieldCheckboxBinding;
import com.legalguide.app.databinding.FieldChipsBinding;
import com.legalguide.app.databinding.FieldDropdownBinding;
import com.legalguide.app.databinding.FieldTextBinding;
import com.legalguide.app.databinding.ItemFormGroupBinding;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Renders admin-defined forms (contract templates, case types) without code changes.
 * Supported types: text, textarea, number, money, date, select, multiselect, checkbox, phone, email, national_id.
 */
public class DynamicFormView extends LinearLayout {

    private interface Holder {
        FormField field();
        @Nullable Object value();
        @Nullable String raw();
        void error(@Nullable String message);
        View focusTarget();
    }

    private final List<Holder> holders = new ArrayList<>();

    public DynamicFormView(Context context) {
        this(context, null);
    }

    public DynamicFormView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setOrientation(VERTICAL);
    }

    public boolean isEmptyForm() {
        return holders.isEmpty();
    }

    public void setFields(@Nullable List<FormField> fields, @Nullable Map<String, Object> initial) {
        removeAllViews();
        holders.clear();
        if (fields == null) return;
        LayoutInflater inf = LayoutInflater.from(getContext());
        String group = null;
        for (FormField f : fields) {
            if (f == null || f.key == null) continue;
            if (!Texts.blank(f.group) && !f.group.equals(group)) {
                group = f.group;
                ItemFormGroupBinding g = ItemFormGroupBinding.inflate(inf, this, true);
                g.formGroupTitle.setText(group);
            }
            Object init = initial != null && initial.containsKey(f.key) ? initial.get(f.key) : f.defaultValue;
            String type = f.type == null ? "text" : f.type;
            switch (type) {
                case "select": holders.add(select(inf, f, init)); break;
                case "multiselect": holders.add(multi(inf, f, init)); break;
                case "checkbox": holders.add(checkbox(inf, f, init)); break;
                case "date": holders.add(date(inf, f, init)); break;
                default: holders.add(text(inf, f, init, type)); break;
            }
        }
    }

    private static String label(FormField f) {
        return f.required ? f.label + " *" : f.label;
    }

    private Holder text(LayoutInflater inf, FormField f, @Nullable Object init, String type) {
        FieldTextBinding b = FieldTextBinding.inflate(inf, this, true);
        b.fieldLayout.setHint(label(f));
        if (!Texts.blank(f.hint)) b.fieldLayout.setHelperText(f.hint);
        if (!Texts.blank(f.placeholder)) b.fieldLayout.setPlaceholderText(f.placeholder);
        switch (type) {
            case "textarea":
                b.fieldInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
                b.fieldInput.setMinLines(3);
                b.fieldInput.setMaxLines(10);
                break;
            case "number":
                b.fieldInput.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED);
                break;
            case "money":
                b.fieldInput.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
                b.fieldLayout.setSuffixText(getContext().getString(R.string.currency_egp));
                break;
            case "phone":
                b.fieldInput.setInputType(InputType.TYPE_CLASS_PHONE);
                break;
            case "email":
                b.fieldInput.setInputType(InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS | InputType.TYPE_CLASS_TEXT);
                break;
            case "national_id":
                b.fieldInput.setInputType(InputType.TYPE_CLASS_NUMBER);
                b.fieldInput.setFilters(new InputFilter[]{new InputFilter.LengthFilter(14)});
                break;
            default:
                b.fieldInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        }
        if (f.maxLength != null && !"national_id".equals(type)) {
            b.fieldLayout.setCounterEnabled(true);
            b.fieldLayout.setCounterMaxLength(f.maxLength);
        }
        if (init != null) b.fieldInput.setText(numberToText(init));
        return new Holder() {
            public FormField field() { return f; }
            @Nullable public String raw() { return Texts.trim(b.fieldInput.getText()); }
            @Nullable public Object value() {
                String v = Texts.asciiDigits(raw());
                if (v.isEmpty()) return null;
                if ("number".equals(type) || "money".equals(type)) {
                    try { return Double.parseDouble(v.replace(",", "")); } catch (NumberFormatException e) { return null; }
                }
                return "phone".equals(type) || "national_id".equals(type) ? v : raw();
            }
            public void error(@Nullable String m) { b.fieldLayout.setError(m); }
            public View focusTarget() { return b.fieldInput; }
        };
    }

    private Holder date(LayoutInflater inf, FormField f, @Nullable Object init) {
        FieldTextBinding b = FieldTextBinding.inflate(inf, this, true);
        b.fieldLayout.setHint(label(f));
        b.fieldLayout.setEndIconMode(com.google.android.material.textfield.TextInputLayout.END_ICON_CUSTOM);
        b.fieldLayout.setEndIconDrawable(R.drawable.ic_calendar);
        b.fieldInput.setFocusable(false);
        b.fieldInput.setClickable(true);
        final String[] iso = {init instanceof String && ((String) init).length() >= 10 ? ((String) init).substring(0, 10) : null};
        if (iso[0] != null) b.fieldInput.setText(Dates.day(iso[0]));
        View.OnClickListener pick = v -> {
            FragmentActivity a = Ui.activity(getContext());
            if (a == null) return;
            MaterialDatePicker.Builder<Long> pb = MaterialDatePicker.Builder.datePicker().setTitleText(f.label);
            if (iso[0] != null) pb.setSelection(Dates.toPickerMillis(LocalDate.parse(iso[0])));
            MaterialDatePicker<Long> p = pb.build();
            p.addOnPositiveButtonClickListener(ms -> {
                LocalDate d = Dates.fromPickerMillis(ms);
                iso[0] = d.toString();
                b.fieldInput.setText(Dates.formatLocalDate(d));
                b.fieldLayout.setError(null);
            });
            p.show(a.getSupportFragmentManager(), "date-" + f.key);
        };
        b.fieldInput.setOnClickListener(pick);
        b.fieldLayout.setEndIconOnClickListener(pick);
        return new Holder() {
            public FormField field() { return f; }
            @Nullable public String raw() { return iso[0]; }
            @Nullable public Object value() { return iso[0]; }
            public void error(@Nullable String m) { b.fieldLayout.setError(m); }
            public View focusTarget() { return b.fieldInput; }
        };
    }

    private Holder select(LayoutInflater inf, FormField f, @Nullable Object init) {
        FieldDropdownBinding b = FieldDropdownBinding.inflate(inf, this, true);
        b.fieldLayout.setHint(label(f));
        List<Option> options = f.options != null ? f.options : new ArrayList<>();
        Ui.dropdown(b.fieldInput, options);
        if (init != null) Ui.selectOption(b.fieldInput, options, String.valueOf(init));
        b.fieldInput.setOnItemClickListener((p, v, pos, id) -> b.fieldLayout.setError(null));
        return new Holder() {
            public FormField field() { return f; }
            @Nullable public String raw() { return Ui.optionValue(options, b.fieldInput.getText()); }
            @Nullable public Object value() { return raw(); }
            public void error(@Nullable String m) { b.fieldLayout.setError(m); }
            public View focusTarget() { return b.fieldInput; }
        };
    }

    private Holder multi(LayoutInflater inf, FormField f, @Nullable Object init) {
        FieldChipsBinding b = FieldChipsBinding.inflate(inf, this, true);
        b.fieldLabel.setText(label(f));
        List<String> selected = new ArrayList<>();
        if (init instanceof Collection) for (Object o : (Collection<?>) init) selected.add(String.valueOf(o));
        List<Option> options = f.options != null ? f.options : new ArrayList<>();
        for (Option o : options) {
            Chip c = Ui.chip(b.fieldChips, o.label, true);
            c.setTag(o.value);
            c.setChecked(selected.contains(o.value));
        }
        return new Holder() {
            public FormField field() { return f; }
            @Nullable public String raw() {
                List<String> v = values();
                return v.isEmpty() ? null : String.valueOf(v.size());
            }
            List<String> values() {
                List<String> out = new ArrayList<>();
                for (int i = 0; i < b.fieldChips.getChildCount(); i++) {
                    View c = b.fieldChips.getChildAt(i);
                    if (c instanceof Chip && ((Chip) c).isChecked()) out.add(String.valueOf(c.getTag()));
                }
                return out;
            }
            @Nullable public Object value() { return values(); }
            public void error(@Nullable String m) { Ui.text(b.fieldError, m); }
            public View focusTarget() { return b.fieldChips; }
        };
    }

    private Holder checkbox(LayoutInflater inf, FormField f, @Nullable Object init) {
        FieldCheckboxBinding b = FieldCheckboxBinding.inflate(inf, this, true);
        b.fieldCheck.setText(label(f));
        b.fieldCheck.setChecked(Boolean.TRUE.equals(init) || "true".equals(String.valueOf(init)));
        return new Holder() {
            public FormField field() { return f; }
            @Nullable public String raw() { return b.fieldCheck.isChecked() ? "true" : null; }
            @Nullable public Object value() { return b.fieldCheck.isChecked(); }
            public void error(@Nullable String m) { b.fieldCheck.setError(m); }
            public View focusTarget() { return b.fieldCheck; }
        };
    }

    /** Validates all fields, shows inline errors and focuses the first invalid one. */
    public boolean validate() {
        View first = null;
        for (Holder h : holders) {
            FormField f = h.field();
            String type = f.type == null ? "text" : f.type;
            String check = ("select".equals(type) || "multiselect".equals(type) || "checkbox".equals(type) || "date".equals(type)) ? "text" : type;
            FieldValidator.Error e = FieldValidator.validate(check, f.required, h.raw(), f.min, f.max, f.maxLength);
            h.error(e == null ? null : message(e, f));
            if (e != null && first == null) first = h.focusTarget();
        }
        if (first != null) first.requestFocus();
        return first == null;
    }

    public Map<String, Object> values() {
        Map<String, Object> out = new LinkedHashMap<>();
        for (Holder h : holders) {
            Object v = h.value();
            if (v != null) out.put(h.field().key, v);
        }
        return out;
    }

    private String message(@NonNull FieldValidator.Error e, FormField f) {
        Context c = getContext();
        switch (e) {
            case REQUIRED: return c.getString(R.string.error_required);
            case INVALID_NUMBER: return c.getString(R.string.error_number);
            case TOO_SMALL: return c.getString(R.string.error_min, numberToText(f.min));
            case TOO_LARGE: return c.getString(R.string.error_max, numberToText(f.max));
            case INVALID_EMAIL: return c.getString(R.string.error_email);
            case INVALID_PHONE: return c.getString(R.string.error_phone);
            case INVALID_NATIONAL_ID: return c.getString(R.string.error_national_id);
            case TOO_LONG: return c.getString(R.string.error_too_long);
            default: return c.getString(R.string.error_invalid);
        }
    }

    private static String numberToText(@Nullable Object o) {
        if (o instanceof Double) {
            double d = (Double) o;
            return d == Math.rint(d) ? String.valueOf((long) d) : String.valueOf(d);
        }
        return o == null ? "" : String.valueOf(o);
    }
}
