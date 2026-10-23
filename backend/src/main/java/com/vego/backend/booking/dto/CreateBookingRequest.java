package com.vego.backend.booking.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/** pickupLabel / dropoffLabel: điểm đón / trả người dùng chọn (bến xe hoặc "Xe trung chuyển"). */
public record CreateBookingRequest(
        @NotBlank(message = "Thiếu mã chuyến")
        String tripId,

        @NotNull(message = "Thiếu ngày đi")
        LocalDate travelDate,

        @NotBlank(message = "Vui lòng chọn ghế")
        @Pattern(regexp = "^[A-Za-z]\\d{1,2}$", message = "Mã ghế không hợp lệ")
        String seat,

        @NotBlank(message = "Vui lòng nhập tên hành khách")
        @Size(min = 2, max = 100, message = "Tên hành khách từ 2 đến 100 ký tự")
        String passengerName,

        @NotBlank(message = "Vui lòng nhập số điện thoại")
        String passengerPhone,

        @Size(max = 200, message = "Điểm đón quá dài")
        String pickupLabel,

        @Size(max = 200, message = "Điểm trả quá dài")
        String dropoffLabel) {
}
