package com.legalguide.app.core.net;

import android.content.Context;

import com.legalguide.app.BuildConfig;
import com.legalguide.app.core.util.Json;

import java.util.concurrent.TimeUnit;

import okhttp3.CertificatePinner;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/** Single place that builds the HTTP stack. */
public final class ApiClient {
    private static volatile ApiService api;
    private static volatile AuthApi authApi;
    private static OkHttpClient baseClient;

    private ApiClient() {}

    public static synchronized void init(Context context) {
        if (api != null) return;
        OkHttpClient.Builder b = new OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(150, TimeUnit.SECONDS)   // AI research / contract generation can take a while
                .writeTimeout(150, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true);

        String pins = BuildConfig.CERT_PINS;
        if (pins != null && !pins.trim().isEmpty()) {
            String host = HttpUrl.get(BuildConfig.API_BASE_URL).host();
            CertificatePinner.Builder cp = new CertificatePinner.Builder();
            for (String p : pins.split(",")) {
                if (!p.trim().isEmpty()) cp.add(host, p.trim());
            }
            b.certificatePinner(cp.build());
        }
        baseClient = b.build();

        OkHttpClient.Builder authed = baseClient.newBuilder()
                .addInterceptor(new AuthInterceptor())
                .authenticator(new TokenAuthenticator());
        if (BuildConfig.DEBUG) {
            HttpLoggingInterceptor log = new HttpLoggingInterceptor();
            log.setLevel(HttpLoggingInterceptor.Level.BASIC);
            log.redactHeader("Authorization");
            authed.addInterceptor(log);
        }
        api = retrofit(authed.build()).create(ApiService.class);
        authApi = retrofit(baseClient.newBuilder().addInterceptor(new AuthInterceptor()).build()).create(AuthApi.class);
    }

    private static Retrofit retrofit(OkHttpClient client) {
        return new Retrofit.Builder()
                .baseUrl(BuildConfig.API_BASE_URL)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create(Json.gson()))
                .build();
    }

    public static ApiService api() {
        ApiService s = api;
        if (s == null) throw new IllegalStateException("ApiClient.init() was not called");
        return s;
    }

    static AuthApi authApi() {
        return authApi;
    }
}
