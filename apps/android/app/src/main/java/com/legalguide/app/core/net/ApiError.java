package com.legalguide.app.core.net;

import androidx.annotation.Nullable;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;

import retrofit2.Response;

/**
 * Normalized API error. Backend envelope: {"error":{"code":"...","message":"...","details":{"fields":{"email":"..."}}}}
 */
public class ApiError {
    public static final String NETWORK = "NETWORK";
    public static final String TIMEOUT = "TIMEOUT";
    public static final String UNKNOWN = "UNKNOWN";
    public static final String UNAUTHORIZED = "UNAUTHORIZED";
    public static final String FORBIDDEN = "FORBIDDEN";
    public static final String NOT_FOUND = "NOT_FOUND";
    public static final String VALIDATION = "VALIDATION";
    public static final String CONFLICT = "CONFLICT";
    public static final String RATE_LIMITED = "RATE_LIMITED";
    public static final String DAILY_LIMIT = "DAILY_LIMIT";
    public static final String PAYMENT_REQUIRED = "PAYMENT_REQUIRED";
    public static final String INSUFFICIENT_CREDITS = "INSUFFICIENT_CREDITS";
    public static final String FREE_USES_EXHAUSTED = "FREE_USES_EXHAUSTED";
    public static final String PLAN_LIMIT = "PLAN_LIMIT";
    public static final String FEATURE_DISABLED = "FEATURE_DISABLED";
    public static final String MAINTENANCE = "MAINTENANCE";
    public static final String FILE_TOO_LARGE = "FILE_TOO_LARGE";
    public static final String SERVER = "SERVER";

    public final int http;
    public final String code;
    @Nullable public final String message;
    public final Map<String, String> fieldErrors;

    public ApiError(int http, String code, @Nullable String message, Map<String, String> fieldErrors) {
        this.http = http;
        this.code = code;
        this.message = message;
        this.fieldErrors = fieldErrors;
    }

    public boolean is(String c) {
        return code.equals(c);
    }

    public boolean isPaywall() {
        return is(INSUFFICIENT_CREDITS) || is(FREE_USES_EXHAUSTED) || is(PAYMENT_REQUIRED) || is(PLAN_LIMIT);
    }

    public static ApiError fromResponse(Response<?> r) {
        String raw = null;
        try {
            if (r.errorBody() != null) raw = r.errorBody().string();
        } catch (IOException ignored) {
        }
        return parse(r.code(), raw);
    }

    /** Pure parser (unit-tested). */
    public static ApiError parse(int http, @Nullable String raw) {
        String code = null;
        String message = null;
        Map<String, String> fields = new HashMap<>();
        if (raw != null && !raw.isEmpty()) {
            try {
                JsonElement root = JsonParser.parseString(raw);
                if (root.isJsonObject()) {
                    JsonObject o = root.getAsJsonObject();
                    JsonObject err = o.has("error") && o.get("error").isJsonObject() ? o.getAsJsonObject("error") : o;
                    if (err.has("code") && err.get("code").isJsonPrimitive()) code = err.get("code").getAsString();
                    if (err.has("message") && err.get("message").isJsonPrimitive()) message = err.get("message").getAsString();
                    if (err.has("details") && err.get("details").isJsonObject()) {
                        JsonObject d = err.getAsJsonObject("details");
                        if (d.has("fields") && d.get("fields").isJsonObject()) {
                            for (Map.Entry<String, JsonElement> e : d.getAsJsonObject("fields").entrySet()) {
                                if (e.getValue().isJsonPrimitive()) fields.put(e.getKey(), e.getValue().getAsString());
                            }
                        }
                    }
                }
            } catch (RuntimeException ignored) {
                // Non-JSON body (proxy error page, etc.)
            }
        }
        if (code == null || code.isEmpty()) code = codeForHttp(http);
        return new ApiError(http, code, message, fields);
    }

    static String codeForHttp(int http) {
        switch (http) {
            case 400:
            case 422: return VALIDATION;
            case 401: return UNAUTHORIZED;
            case 402: return PAYMENT_REQUIRED;
            case 403: return FORBIDDEN;
            case 404: return NOT_FOUND;
            case 409: return CONFLICT;
            case 413: return FILE_TOO_LARGE;
            case 429: return RATE_LIMITED;
            case 503: return MAINTENANCE;
            default: return http >= 500 ? SERVER : UNKNOWN;
        }
    }

    public static ApiError fromThrowable(Throwable t) {
        if (t instanceof SocketTimeoutException || t instanceof InterruptedIOException) return new ApiError(0, TIMEOUT, null, new HashMap<>());
        if (t instanceof UnknownHostException || t instanceof IOException) return new ApiError(0, NETWORK, null, new HashMap<>());
        return new ApiError(0, UNKNOWN, t.getMessage(), new HashMap<>());
    }
}
