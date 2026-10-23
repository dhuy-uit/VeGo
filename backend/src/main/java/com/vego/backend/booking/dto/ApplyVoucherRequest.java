package com.vego.backend.booking.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ApplyVoucherRequest(
        @NotBlank(message = "Vui lòng nhập mã giảm giá")
        @Size(max = 30, message = "Mã giảm giá không hợp lệ")
        String code) {
}
