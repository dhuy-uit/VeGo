package com.vego.backend.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record UpdateProfileRequest(
        @NotBlank(message = "Vui lòng nhập họ và tên")
        @Size(min = 2, max = 100, message = "Họ và tên từ 2 đến 100 ký tự")
        String fullName,

        @NotBlank(message = "Vui lòng nhập số điện thoại")
        String phone,

        @NotBlank(message = "Vui lòng nhập email")
        @Email(message = "Email chưa đúng định dạng")
        @Size(max = 255, message = "Email quá dài")
        String email,

        @Past(message = "Ngày sinh phải trước hôm nay")
        LocalDate birthDate) {
}
