package com.vego.backend.auth.dto;

import jakarta.validation.constraints.NotBlank;

/** Dùng cho cả /auth/refresh và /auth/logout. */
public record RefreshRequest(
        @NotBlank(message = "Thiếu refresh token")
        String refreshToken) {
}
