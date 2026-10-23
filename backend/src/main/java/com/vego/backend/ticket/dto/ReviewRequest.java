package com.vego.backend.ticket.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/** tags: CLEAN (xe sạch sẽ) | ONTIME (đúng giờ) | STAFF (nhân viên thân thiện). */
public record ReviewRequest(
        @NotNull(message = "Vui lòng chọn số sao")
        @Min(value = 1, message = "Số sao từ 1 đến 5")
        @Max(value = 5, message = "Số sao từ 1 đến 5")
        Integer rating,

        @Size(max = 5, message = "Chọn tối đa 5 nhãn")
        List<String> tags,

        @Size(max = 1000, message = "Nhận xét tối đa 1000 ký tự")
        String comment) {
}
