package com.example.vego.data.api;

import androidx.annotation.Nullable;

import java.io.IOException;

import okhttp3.Authenticator;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.Route;

/**
 * Khi server trả 401 cho request có token: tự gọi /auth/refresh lấy token mới rồi gửi lại request.
 * Làm mới thất bại (refresh token hết hạn / bị thu hồi) -> xóa phiên và báo về màn Đăng nhập.
 * Đồng bộ (synchronized) để nhiều request hỏng cùng lúc chỉ làm mới một lần,
 * tránh server tưởng refresh token bị dùng lại.
 */
final class TokenAuthenticator implements Authenticator {

    private final TokenStore tokens;
    private final ApiClient.RefreshCaller refresher;
    private final Runnable onSessionExpired;

    TokenAuthenticator(TokenStore tokens, ApiClient.RefreshCaller refresher, Runnable onSessionExpired) {
        this.tokens = tokens;
        this.refresher = refresher;
        this.onSessionExpired = onSessionExpired;
    }

    @Nullable
    @Override
    public Request authenticate(@Nullable Route route, Response response) {
        String sentAuth = response.request().header("Authorization");
        if (sentAuth == null || priorResponses(response) >= 2) {
            return null; // request không có token (vd đăng nhập sai mật khẩu) -> để app tự xử lý lỗi
        }
        synchronized (this) {
            String current = tokens.accessToken();
            if (current != null && !sentAuth.equals("Bearer " + current)) {
                // Request khác vừa làm mới xong -> dùng luôn token mới
                return withToken(response.request(), current);
            }
            String refresh = tokens.refreshToken();
            if (refresh == null) {
                return null;
            }
            try {
                retrofit2.Response<ApiModels.AuthResponse> res = refresher.refresh(refresh);
                if (res.isSuccessful() && res.body() != null) {
                    tokens.save(res.body());
                    return withToken(response.request(), res.body().accessToken);
                }
                if (res.code() == 401 || res.code() == 403) {
                    tokens.clear();
                    onSessionExpired.run();
                }
            } catch (IOException e) {
                // Mất mạng: giữ nguyên phiên, để request báo lỗi kết nối
            }
            return null;
        }
    }

    private static Request withToken(Request request, String token) {
        return request.newBuilder().header("Authorization", "Bearer " + token).build();
    }

    private static int priorResponses(Response response) {
        int count = 0;
        while ((response = response.priorResponse()) != null) count++;
        return count;
    }
}
