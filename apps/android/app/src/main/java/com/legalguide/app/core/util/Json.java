package com.legalguide.app.core.util;

import androidx.annotation.Nullable;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;

import java.util.LinkedHashMap;
import java.util.Map;

public final class Json {
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    private Json() {}

    public static Gson gson() { return GSON; }

    public static String toJson(Object o) { return GSON.toJson(o); }

    @Nullable
    public static <T> T fromJson(@Nullable String s, Class<T> type) {
        if (s == null || s.isEmpty()) return null;
        try {
            return GSON.fromJson(s, type);
        } catch (JsonSyntaxException e) {
            return null;
        }
    }

    /** Builds a request body: map("k1", v1, "k2", v2). Null values are skipped. */
    public static Map<String, Object> map(Object... kv) {
        if (kv.length % 2 != 0) throw new IllegalArgumentException("Key/value pairs expected");
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            if (kv[i + 1] != null) m.put(String.valueOf(kv[i]), kv[i + 1]);
        }
        return m;
    }
}
