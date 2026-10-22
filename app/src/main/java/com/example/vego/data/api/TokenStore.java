package com.example.vego.data.api;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.Nullable;

import com.google.gson.Gson;

/**
 * Lưu access token, refresh token và hồ sơ người dùng (bản cache) trong SharedPreferences
 * để mở lại app không phải đăng nhập lại.
 */
public final class TokenStore {

    private static final String PREFS = "vego_session";
    private static final String KEY_ACCESS = "access_token";
    private static final String KEY_REFRESH = "refresh_token";
    private static final String KEY_USER = "user_json";

    private final SharedPreferences prefs;
    private final Gson gson;

    TokenStore(Context context, Gson gson) {
        this.prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        this.gson = gson;
    }

    public synchronized void save(ApiModels.AuthResponse auth) {
        prefs.edit()
                .putString(KEY_ACCESS, auth.accessToken)
                .putString(KEY_REFRESH, auth.refreshToken)
                .putString(KEY_USER, auth.user == null ? null : gson.toJson(auth.user))
                .apply();
    }

    public synchronized void saveUser(@Nullable ApiModels.User user) {
        prefs.edit().putString(KEY_USER, user == null ? null : gson.toJson(user)).apply();
    }

    public synchronized void clear() {
        prefs.edit().clear().apply();
    }

    @Nullable public synchronized String accessToken() { return prefs.getString(KEY_ACCESS, null); }

    @Nullable public synchronized String refreshToken() { return prefs.getString(KEY_REFRESH, null); }

    public synchronized boolean isLoggedIn() { return refreshToken() != null; }

    @Nullable
    public synchronized ApiModels.User user() {
        String json = prefs.getString(KEY_USER, null);
        if (json == null) return null;
        try {
            return gson.fromJson(json, ApiModels.User.class);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
