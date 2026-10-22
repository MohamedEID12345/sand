package com.legalguide.app.core.util;

import androidx.annotation.Nullable;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/** Currency & number formatting (pure Java, unit-tested). */
public final class Money {
    private static final DecimalFormatSymbols SYM = DecimalFormatSymbols.getInstance(Locale.US);

    private Money() {}

    public static String format(double amount, @Nullable String currency) {
        DecimalFormat f = new DecimalFormat(amount == Math.rint(amount) ? "#,##0" : "#,##0.00", SYM);
        return f.format(amount) + " " + symbol(currency);
    }

    public static String symbol(@Nullable String currency) {
        if (currency == null || currency.isEmpty() || "EGP".equalsIgnoreCase(currency)) return "ج.م";
        if ("USD".equalsIgnoreCase(currency)) return "$";
        return currency;
    }

    public static String number(long n) {
        return new DecimalFormat("#,##0", SYM).format(n);
    }

    public static String signed(long n) {
        return (n > 0 ? "+" : "") + number(n);
    }
}
