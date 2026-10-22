package com.vego.backend.user.dto;

import com.vego.backend.auth.dto.PasswordRules;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ChangePasswordRequest(
        @NotBlank(message = "Vui lòng nhập mật khẩu hiện tại")
        String currentPassword,

        @NotBlank(message = "Vui lòng nhập mật khẩu mới")
        @Pattern(regexp = PasswordRules.STRONG, message = PasswordRules.STRONG_MESSAGE)
        String newPassword,

        String device) {
}
