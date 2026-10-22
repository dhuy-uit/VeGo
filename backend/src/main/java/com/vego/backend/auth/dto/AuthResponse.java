package com.vego.backend.auth.dto;

import com.vego.backend.user.dto.UserResponse;

/** Kết quả đăng nhập / làm mới token. expiresIn tính bằng giây. */
public record AuthResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn,
        long refreshExpiresIn,
        UserResponse user) {
}
