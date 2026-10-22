package com.vego.backend.security;

/** Người dùng đã xác thực, lấy từ access token (dùng với @AuthenticationPrincipal). */
public record AuthUser(long id, String email, String role) {
}
