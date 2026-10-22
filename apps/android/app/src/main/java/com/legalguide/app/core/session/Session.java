package com.legalguide.app.core.session;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Looper;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.legalguide.app.core.util.Json;
import com.legalguide.app.data.model.AiCost;
import com.legalguide.app.data.model.AppConfig;
import com.legalguide.app.data.model.Bootstrap;
import com.legalguide.app.data.model.CaseMeta;
import com.legalguide.app.data.model.User;
import com.legalguide.app.data.model.Wallet;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Authenticated session + server-driven app state (user, wallet, subscription, permissions,
 * feature flags, lookups). Nothing here is authoritative: the backend re-checks everything.
 */
public final class Session {
    private static volatile Session instance;

    private final SecureStore secure;
    private final SharedPreferences cache;
    private volatile String accessToken;
    private volatile String refreshToken;
    private final String deviceId;

    private final MutableLiveData<Bootstrap> bootstrap = new MutableLiveData<>();
    private final MutableLiveData<Long> logoutEvent = new MutableLiveData<>();
    private final MutableLiveData<Integer> unread = new MutableLiveData<>(0);

    @Nullable private AppConfig config;
    @Nullable private CaseMeta caseMeta;
    private List<AiCost> aiCosts = new ArrayList<>();

    private Session(Context ctx) {
        secure = new SecureStore(ctx);
        cache = ctx.getSharedPreferences("lg_cache", Context.MODE_PRIVATE);
        accessToken = secure.get("access");
        refreshToken = secure.get("refresh");
        String id = cache.getString("device_id", null);
        if (id == null) {
            id = UUID.randomUUID().toString();
            cache.edit().putString("device_id", id).apply();
        }
        deviceId = id;
        Bootstrap cached = Json.fromJson(cache.getString("bootstrap", null), Bootstrap.class);
        if (cached != null && accessToken != null) bootstrap.setValue(cached);
        config = Json.fromJson(cache.getString("config", null), AppConfig.class);
    }

    public static void init(Context ctx) {
        if (instance == null) {
            synchronized (Session.class) {
                if (instance == null) instance = new Session(ctx.getApplicationContext());
            }
        }
    }

    public static Session get() {
        if (instance == null) throw new IllegalStateException("Session.init() was not called");
        return instance;
    }

    // ---------- Tokens ----------
    @Nullable public String accessToken() { return accessToken; }
    @Nullable public String refreshToken() { return refreshToken; }
    public String deviceId() { return deviceId; }
    public boolean isLoggedIn() { return accessToken != null && refreshToken != null; }

    public synchronized void saveTokens(String access, String refresh) {
        accessToken = access;
        refreshToken = refresh;
        secure.put("access", access);
        secure.put("refresh", refresh);
    }

    /** Clears credentials and notifies open screens (they route to login). */
    public synchronized void forceLogout() {
        boolean wasLoggedIn = accessToken != null;
        clearLocal();
        if (wasLoggedIn) logoutEvent.postValue(System.currentTimeMillis());
    }

    public synchronized void clearLocal() {
        accessToken = null;
        refreshToken = null;
        secure.clear();
        cache.edit().remove("bootstrap").remove("case_meta").apply();
        caseMeta = null;
        publish(bootstrap, null);
        publish(unread, 0);
        Prefs.clearUserScoped();
    }

    public LiveData<Long> logoutEvents() { return logoutEvent; }

    // ---------- Bootstrap ----------
    public LiveData<Bootstrap> bootstrap() { return bootstrap; }

    @Nullable public Bootstrap current() { return bootstrap.getValue(); }

    public void setBootstrap(@Nullable Bootstrap b) {
        if (b == null) return;
        cache.edit().putString("bootstrap", Json.toJson(b)).apply();
        publish(bootstrap, b);
        if (b.unreadNotifications != null) publish(unread, b.unreadNotifications);
    }

    @Nullable public User user() {
        Bootstrap b = current();
        return b != null ? b.user : null;
    }

    @Nullable public Wallet wallet() {
        Bootstrap b = current();
        return b != null ? b.wallet : null;
    }

    public void updateWallet(@Nullable Wallet w) {
        Bootstrap b = current();
        if (w == null || b == null) return;
        Bootstrap copy = Json.fromJson(Json.toJson(b), Bootstrap.class);
        if (copy == null) return;
        copy.wallet = w;
        setBootstrap(copy);
    }

    public void updateUser(@Nullable User u) {
        Bootstrap b = current();
        if (u == null || b == null) return;
        Bootstrap copy = Json.fromJson(Json.toJson(b), Bootstrap.class);
        if (copy == null) return;
        copy.user = u;
        setBootstrap(copy);
    }

    /** RBAC hint for hiding UI. The server enforces permissions regardless. */
    public boolean can(String permission) {
        Bootstrap b = current();
        return b != null && b.permissions != null && (b.permissions.contains(permission) || b.permissions.contains("*"));
    }

    /** Feature flag (admin-controlled). Unknown flags default to enabled. */
    public boolean feature(String key) {
        Bootstrap b = current();
        if (b != null && b.features != null && b.features.containsKey(key)) return Boolean.TRUE.equals(b.features.get(key));
        if (config != null && config.features != null && config.features.containsKey(key)) return Boolean.TRUE.equals(config.features.get(key));
        return true;
    }

    // ---------- Unread notifications ----------
    public LiveData<Integer> unreadCount() { return unread; }
    public void setUnread(int n) { publish(unread, Math.max(0, n)); }

    // ---------- Public config & lookups ----------
    @Nullable public AppConfig config() { return config; }

    public void setConfig(@Nullable AppConfig c) {
        if (c == null) return;
        config = c;
        cache.edit().putString("config", Json.toJson(c)).apply();
    }

    @Nullable public CaseMeta caseMeta() {
        if (caseMeta == null) caseMeta = Json.fromJson(cache.getString("case_meta", null), CaseMeta.class);
        return caseMeta;
    }

    public void setCaseMeta(@Nullable CaseMeta m) {
        if (m == null) return;
        caseMeta = m;
        cache.edit().putString("case_meta", Json.toJson(m)).apply();
    }

    public List<AiCost> aiCosts() { return aiCosts; }

    /** setValue on the main thread (so reads right after are consistent), postValue otherwise. */
    private static <T> void publish(MutableLiveData<T> ld, @Nullable T value) {
        if (Looper.myLooper() == Looper.getMainLooper()) ld.setValue(value);
        else ld.postValue(value);
    }

    public void setAiCosts(@Nullable List<AiCost> costs) {
        if (costs != null) aiCosts = costs;
    }

    public int costOf(String operation) {
        for (AiCost c : aiCosts) if (operation.equals(c.operation)) return c.credits;
        return -1;
    }
}
