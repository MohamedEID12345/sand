package com.legalguide.app.core.net;

import androidx.annotation.NonNull;

import com.legalguide.app.BuildConfig;
import com.legalguide.app.core.session.Session;

import java.io.IOException;

import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

/** Adds client identification and the bearer access token. */
final class AuthInterceptor implements Interceptor {

    @NonNull
    @Override
    public Response intercept(@NonNull Chain chain) throws IOException {
        Request req = chain.request();
        Session s = Session.get();
        Request.Builder b = req.newBuilder()
                .header("Accept", "application/json")
                .header("Accept-Language", "ar")
                .header("X-Client", "android/" + BuildConfig.VERSION_NAME + "/" + BuildConfig.VERSION_CODE)
                .header("X-Device-Id", s.deviceId());
        String token = s.accessToken();
        if (token != null && req.header("Authorization") == null) {
            b.header("Authorization", "Bearer " + token);
        }
        return chain.proceed(b.build());
    }
}
