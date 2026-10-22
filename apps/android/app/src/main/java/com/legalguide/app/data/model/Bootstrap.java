package com.legalguide.app.data.model;

import java.util.List;
import java.util.Map;

/** Everything the app needs after login: GET /me/bootstrap. */
public class Bootstrap {
    public User user;
    public Wallet wallet;
    public Subscription subscription;
    public List<String> permissions;
    public Map<String, Boolean> features;
    public Integer unreadNotifications;
}
