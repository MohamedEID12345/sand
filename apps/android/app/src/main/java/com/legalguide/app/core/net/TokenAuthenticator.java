package com.legalguide.app.core.net;

import androidx.annotation.Nullable;

import com.legalguide.app.core.session.Session;
import com.legalguide.app.core.util.Json;
import com.legalguide.app.data.model.AuthResponse;

import java.io.IOException;

import okhttp3.Authenticator;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.Route;

/**
 * On 401: refreshes the access token once (rotating refresh token) and retries.
 * Concurrent 401s are serialized; if another thread already refreshed, the new token is reused.
 */
final class TokenAuthenticator implements Authenticator {
    private static final Object LOCK = new Object();

    @Nullable
    @Override
    public Request authenticate(@Nullable Route route, Response response) throws IOException {
        Request req = response.request();
        String path = req.url().encodedPath();
        if (path.endsWith("/auth/refresh") || path.endsWith("/auth/login") || path.endsWith("/auth/register")) return null;
        if (priorCount(response) >= 2) {
            Session.get().forceLogout();
            return null;
        }
        String sent = bearer(req.header("Authorization"));
        if (sent == null) return null;

        synchronized (LOCK) {
            Session s = Session.get();
            String current = s.accessToken();
            if (current != null && !current.equals(sent)) return withToken(req, current);

            String refresh = s.refreshToken();
            if (refresh == null) {
                s.forceLogout();
                return null;
            }
            retrofit2.Response<AuthResponse> r = ApiClient.authApi()
                    .refresh(Json.map("refreshToken", refresh, "deviceId", s.deviceId()))
                    .execute();
            AuthResponse body = r.body();
            if (r.isSuccessful() && body != null && body.accessToken != null) {
                s.saveTokens(body.accessToken, body.refreshToken != null ? body.refreshToken : refresh);
                return withToken(req, body.accessToken);
            }
            if (r.code() == 400 || r.code() == 401 || r.code() == 403) s.forceLogout();
            return null;
        }
    }

    private static Request withToken(Request req, String token) {
        return req.newBuilder().header("Authorization", "Bearer " + token).build();
    }

    @Nullable
    private static String bearer(@Nullable String header) {
        if (header == null || !header.startsWith("Bearer ")) return null;
        return header.substring(7);
    }

    private static int priorCount(Response response) {
        int n = 1;
        Response r = response.priorResponse();
        while (r != null) {
            n++;
            r = r.priorResponse();
        }
        return n;
    }
}
