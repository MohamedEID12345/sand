package com.legalguide.app.core.util;

import androidx.annotation.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;

/** Date helpers. Server sends ISO-8601 (UTC instants or plain dates); UI shows Arabic month names, Latin digits. */
public final class Dates {
    public static final Locale AR = new Locale("ar", "EG");
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d MMMM yyyy", AR);
    private static final DateTimeFormatter DAY_SHORT = DateTimeFormatter.ofPattern("d MMM", AR);
    private static final DateTimeFormatter WEEKDAY = DateTimeFormatter.ofPattern("EEEE d MMMM", AR);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("h:mm a", AR);

    private Dates() {}

    public static ZoneId zone() { return ZoneId.systemDefault(); }

    @Nullable
    public static ZonedDateTime parse(@Nullable String iso) {
        if (iso == null || iso.isEmpty()) return null;
        try {
            if (iso.length() == 10) return LocalDate.parse(iso).atStartOfDay(zone());
            if (iso.endsWith("Z") || iso.matches(".*[+-]\\d\\d:?\\d\\d$")) return OffsetDateTime.parse(iso).atZoneSameInstant(zone());
            return LocalDateTime.parse(iso).atZone(zone());
        } catch (DateTimeParseException e) {
            try {
                return Instant.parse(iso).atZone(zone());
            } catch (DateTimeParseException e2) {
                return null;
            }
        }
    }

    public static boolean isDateOnly(@Nullable String iso) { return iso != null && iso.length() == 10; }

    public static String day(@Nullable String iso) {
        ZonedDateTime z = parse(iso);
        return z == null ? "—" : DAY.format(z);
    }

    public static String dayShort(@Nullable String iso) {
        ZonedDateTime z = parse(iso);
        return z == null ? "—" : DAY_SHORT.format(z);
    }

    public static String weekday(@Nullable String iso) {
        ZonedDateTime z = parse(iso);
        return z == null ? "—" : WEEKDAY.format(z);
    }

    public static String time(@Nullable String iso) {
        ZonedDateTime z = parse(iso);
        return z == null || isDateOnly(iso) ? "" : TIME.format(z);
    }

    public static String dayTime(@Nullable String iso) {
        ZonedDateTime z = parse(iso);
        if (z == null) return "—";
        return isDateOnly(iso) ? DAY.format(z) : DAY.format(z) + " • " + TIME.format(z);
    }

    /** "منذ ٥ دقائق" style relative time for feeds. */
    public static String relative(@Nullable String iso) {
        ZonedDateTime z = parse(iso);
        if (z == null) return "";
        long mins = Duration.between(z.toInstant(), Instant.now()).toMinutes();
        if (mins < 1) return "الآن";
        if (mins < 60) return "منذ " + mins + " د";
        long hours = mins / 60;
        if (hours < 24) return "منذ " + hours + " س";
        long days = hours / 24;
        if (days < 7) return "منذ " + days + " يوم";
        return DAY.format(z);
    }

    /** Days from today (local) to the given date; negative if past. */
    public static long daysUntil(@Nullable String iso) {
        ZonedDateTime z = parse(iso);
        if (z == null) return Long.MIN_VALUE;
        return Duration.between(LocalDate.now(zone()).atStartOfDay(zone()), z.toLocalDate().atStartOfDay(zone())).toDays();
    }

    public static String isoDate(LocalDate d) { return d.toString(); }

    public static String isoInstant(ZonedDateTime z) { return z.toInstant().toString(); }

    /** MaterialDatePicker returns UTC midnight millis. */
    public static LocalDate fromPickerMillis(long utcMillis) {
        return Instant.ofEpochMilli(utcMillis).atZone(ZoneOffset.UTC).toLocalDate();
    }

    public static long toPickerMillis(LocalDate d) {
        return d.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
    }

    public static String formatLocalDate(LocalDate d) { return DAY.format(d); }

    public static String groupKey(@Nullable String iso) {
        ZonedDateTime z = parse(iso);
        if (z == null) return "—";
        long d = daysUntil(iso);
        if (d == 0) return "اليوم • " + DAY.format(z);
        if (d == 1) return "غدًا • " + DAY.format(z);
        if (d == -1) return "أمس • " + DAY.format(z);
        return WEEKDAY.format(z) + " " + z.getYear();
    }
}
