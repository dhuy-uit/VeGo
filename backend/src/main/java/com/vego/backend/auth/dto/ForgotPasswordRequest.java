package com.vego.backend.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record ForgotPasswordRequest(
        @NotBlank(message = "Vui lòng nhập số điện thoại hoặc email")
        String account) {
}
