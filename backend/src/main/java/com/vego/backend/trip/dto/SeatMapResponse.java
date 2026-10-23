package com.vego.backend.trip.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * Sơ đồ ghế của một chuyến trong một ngày. taken: mọi ghế không chọn được (đã bán / đang có người giữ).
 * decks[].rows: các hàng ghế từ đầu xe tới cuối xe, mỗi hàng tối đa 3 ghế.
 */
public record SeatMapResponse(
        String tripId,
        LocalDate date,
        String busType,
        int totalSeats,
        int seatsLeft,
        int price,
        List<Deck> decks,
        List<String> taken) {

    public record Deck(String code, String name, List<List<Seat>> rows) { }

    public record Seat(String code, boolean available) { }
}
