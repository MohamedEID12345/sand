package com.legalguide.app.core.util;

import androidx.annotation.Nullable;

import java.util.regex.Pattern;

/** Pure validation rules shared by static forms and the dynamic form engine (unit-tested). */
public final class FieldValidator {
    public enum Error { REQUIRED, INVALID_NUMBER, TOO_SMALL, TOO_LARGE, INVALID_EMAIL, INVALID_PHONE, INVALID_NATIONAL_ID, TOO_LONG, WEAK_PASSWORD }

    private static final Pattern EMAIL = Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    /** Egyptian mobile: 010/011/012/015 + 8 digits, optional +20 / 0020 prefix. */
    private static final Pattern EG_MOBILE = Pattern.compile("^(\\+20|0020|20)?0?1[0125]\\d{8}$");
    /** Egyptian national ID: 14 digits, century digit 2 or 3. */
    private static final Pattern EG_NID = Pattern.compile("^[23]\\d{13}$");

    private FieldValidator() {}

    @Nullable
    public static Error validate(String type, boolean required, @Nullable String raw,
                                 @Nullable Double min, @Nullable Double max, @Nullable Integer maxLength) {
        String v = raw == null ? "" : Texts.asciiDigits(raw).trim();
        if (v.isEmpty()) return required ? Error.REQUIRED : null;
        if (maxLength != null && v.length() > maxLength) return Error.TOO_LONG;
        switch (type == null ? "text" : type) {
            case "number":
            case "money":
                try {
                    double d = Double.parseDouble(v.replace(",", ""));
                    if (min != null && d < min) return Error.TOO_SMALL;
                    if (max != null && d > max) return Error.TOO_LARGE;
                } catch (NumberFormatException e) {
                    return Error.INVALID_NUMBER;
                }
                return null;
            case "email":
                return EMAIL.matcher(v).matches() ? null : Error.INVALID_EMAIL;
            case "phone":
                return isEgyptianMobile(v) ? null : Error.INVALID_PHONE;
            case "national_id":
                return EG_NID.matcher(v).matches() ? null : Error.INVALID_NATIONAL_ID;
            default:
                return null;
        }
    }

    public static boolean isEmail(@Nullable String v) {
        return v != null && EMAIL.matcher(v.trim()).matches();
    }

    public static boolean isEgyptianMobile(@Nullable String v) {
        if (v == null) return false;
        String n = Texts.asciiDigits(v).replaceAll("[\\s-]", "");
        return EG_MOBILE.matcher(n).matches();
    }

    /** At least 8 chars with a letter and a digit. */
    public static boolean isStrongPassword(@Nullable String v) {
        if (v == null || v.length() < 8) return false;
        boolean letter = false, digit = false;
        for (char c : v.toCharArray()) {
            if (Character.isLetter(c)) letter = true;
            if (Character.isDigit(c)) digit = true;
        }
        return letter && digit;
    }
}
