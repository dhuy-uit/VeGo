package com.vego.backend.ticket.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/** Đổi vé sang chuyến / ngày / ghế khác. pickupLabel, dropoffLabel bỏ trống thì dùng bến chính của chuyến mới. */
public record ExchangeRequest(
        @NotBlank(message = "Vui lòng chọn chuyến mới") String tripId,
        @NotNull(message = "Vui lòng chọn ngày đi mới") LocalDate travelDate,
        @NotBlank(message = "Vui lòng chọn ghế") @Size(max = 10) String seat,
        @Size(max = 200) String pickupLabel,
        @Size(max = 200) String dropoffLabel) {
}
