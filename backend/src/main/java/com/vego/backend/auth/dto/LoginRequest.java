package com.vego.backend.auth.dto;

import jakarta.validation.constraints.NotBlank;

/** account: số điện thoại hoặc email. device: tên thiết bị (không bắt buộc). */
public record LoginRequest(
        @NotBlank(message = "Vui lòng nhập số điện thoại hoặc email")
        String account,

        @NotBlank(message = "Vui lòng nhập mật khẩu")
        String password,

        String device) {
}
