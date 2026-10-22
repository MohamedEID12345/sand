package com.legalguide.app.core.net;

import com.legalguide.app.data.model.AuthResponse;

import java.util.Map;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;

/** Token refresh endpoint, served by a client without the authenticator (avoids recursion). */
interface AuthApi {
    @POST("auth/refresh") Call<AuthResponse> refresh(@Body Map<String, Object> body);
}
