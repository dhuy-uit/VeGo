package com.vego.backend.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank(message = "Vui lòng nhập họ và tên")
        @Size(min = 2, max = 100, message = "Họ và tên từ 2 đến 100 ký tự")
        String fullName,

        @NotBlank(message = "Vui lòng nhập số điện thoại")
        String phone,

        @NotBlank(message = "Vui lòng nhập email")
        @Email(message = "Email chưa đúng định dạng")
        @Size(max = 255, message = "Email quá dài")
        String email,

        @NotBlank(message = "Vui lòng nhập mật khẩu")
        @Pattern(regexp = PasswordRules.BASIC, message = PasswordRules.BASIC_MESSAGE)
        String password) {
}
