package com.vego.backend.home.dto;

import java.time.LocalDate;

/**
 * Một chuyến xe vào một ngày cụ thể. Các trường khớp lớp Trip trong app
 * (departMinutes = phút trong ngày, vd 22:00 = 1320).
 */
public record TripResponse(
        String id,
        String operator,
        String busName,
        String busType,
        double rating,
        LocalDate departureDate,
        String departTime,
        String arriveTime,
        int arriveDayOffset,
        int departMinutes,
        int durationMinutes,
        int price,
        int seatsLeft,
        int totalSeats,
        String origin,
        String destination,
        String pickupStation,
        String pickupAddress,
        String dropoffStation,
        String dropoffAddress) {
}
