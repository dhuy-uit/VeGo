package com.vego.backend.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ResetPasswordRequest(
        @NotBlank(message = "Thiếu mã khôi phục")
        String token,

        @NotBlank(message = "Vui lòng nhập mật khẩu mới")
        @Pattern(regexp = PasswordRules.STRONG, message = PasswordRules.STRONG_MESSAGE)
        String newPassword) {
}
