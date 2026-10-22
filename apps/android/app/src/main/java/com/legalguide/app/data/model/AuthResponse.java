package com.legalguide.app.data.model;


/** Login/register/refresh response. */
public class AuthResponse {
    public String accessToken;
    public String refreshToken;
    public long expiresIn;
    public User user;
}
