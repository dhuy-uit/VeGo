package com.vego.backend.trip.dto;

import com.vego.backend.home.dto.TripResponse;

import java.time.LocalDate;
import java.util.List;

/**
 * Gợi ý "Dành riêng cho bạn". basedOn: chuyến đã đi gần nhất làm căn cứ (null nếu chưa có lịch sử).
 * main: chuyến gợi ý chính, more: tối đa 2 lựa chọn thêm.
 */
public record SuggestionResponse(BasedOn basedOn, LocalDate date, TripResponse main, List<TripResponse> more) {

    /** slot: "buổi sáng" / "buổi chiều" / "buổi tối". */
    public record BasedOn(String ticketCode, String origin, String destination, LocalDate travelDate,
                          String operator, int departMinutes, String slot) { }
}
