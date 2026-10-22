package com.example.vego.data.api;

import androidx.annotation.NonNull;

import java.io.IOException;

import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

/** Gắn "Authorization: Bearer &lt;access token&gt;" vào mọi request, trừ request có header X-No-Auth. */
final class AuthInterceptor implements Interceptor {

    private final TokenStore tokens;

    AuthInterceptor(TokenStore tokens) {
        this.tokens = tokens;
    }

    @NonNull
    @Override
    public Response intercept(@NonNull Chain chain) throws IOException {
        Request request = chain.request();
        if (request.header("X-No-Auth") != null) {
            return chain.proceed(request.newBuilder().removeHeader("X-No-Auth").build());
        }
        String token = tokens.accessToken();
        if (token == null || request.header("Authorization") != null) {
            return chain.proceed(request);
        }
        return chain.proceed(request.newBuilder().header("Authorization", "Bearer " + token).build());
    }
}
