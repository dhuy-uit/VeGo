package com.vego.backend.user.dto;

import java.time.Instant;
import java.time.LocalDate;

/** Thông tin hồ sơ trả về cho app. phone luôn là 10 chữ số, vd "0901234567". */
public record UserResponse(
        long id,
        String fullName,
        String givenName,
        String phone,
        String email,
        LocalDate birthDate,
        String avatarUrl,
        String role,
        Instant createdAt) {
}
