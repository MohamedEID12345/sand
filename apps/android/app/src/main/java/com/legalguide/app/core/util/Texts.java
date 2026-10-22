package com.legalguide.app.core.util;

import androidx.annotation.Nullable;

import java.util.List;
import java.util.Locale;

public final class Texts {
    private Texts() {}

    public static boolean blank(@Nullable CharSequence s) {
        return s == null || s.toString().trim().isEmpty();
    }

    public static String orDash(@Nullable String s) {
        return blank(s) ? "—" : s;
    }

    public static String trim(@Nullable CharSequence s) {
        return s == null ? "" : s.toString().trim();
    }

    @Nullable
    public static String nullIfBlank(@Nullable CharSequence s) {
        return blank(s) ? null : s.toString().trim();
    }

    /** Two-letter initials for avatars (works for Arabic names). */
    public static String initials(@Nullable String name) {
        if (blank(name)) return "؟";
        String[] parts = name.trim().split("\\s+");
        if (parts.length == 1) return parts[0].substring(0, 1);
        return parts[0].substring(0, 1) + " " + parts[1].substring(0, 1);
    }

    public static String fileSize(long bytes) {
        if (bytes <= 0) return "";
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format(Locale.US, "%.0f KB", bytes / 1024.0);
        return String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0));
    }

    public static String join(@Nullable List<String> items, String sep) {
        if (items == null || items.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (String s : items) {
            if (blank(s)) continue;
            if (sb.length() > 0) sb.append(sep);
            sb.append(s);
        }
        return sb.toString();
    }

    public static String joinNonBlank(String sep, String... parts) {
        StringBuilder sb = new StringBuilder();
        for (String s : parts) {
            if (blank(s)) continue;
            if (sb.length() > 0) sb.append(sep);
            sb.append(s.trim());
        }
        return sb.toString();
    }

    /** Normalizes Arabic-Indic / Persian digits to ASCII (users often type ٠١٢). */
    public static String asciiDigits(@Nullable String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length());
        for (char c : s.toCharArray()) {
            if (c >= '\u0660' && c <= '\u0669') sb.append((char) ('0' + (c - '\u0660')));
            else if (c >= '\u06F0' && c <= '\u06F9') sb.append((char) ('0' + (c - '\u06F0')));
            else sb.append(c);
        }
        return sb.toString();
    }
}
