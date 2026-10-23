package com.vego.backend.trip.dto;

import com.vego.backend.home.dto.TripResponse;

import java.time.LocalDate;
import java.util.List;

/**
 * Kết quả tìm chuyến. origin / destination là tên chuẩn trong CSDL (vd người dùng gõ "da lat" -> "Đà Lạt").
 * operators, minPrice, maxPrice: của cả tuyến (trước khi lọc) để app dựng màn Bộ lọc.
 * routeFound = false: không có tuyến nào khớp điểm đi / điểm đến.
 */
public record TripSearchResponse(
        String origin,
        String destination,
        LocalDate date,
        boolean routeFound,
        int total,
        List<TripResponse> trips,
        List<String> operators,
        Integer minPrice,
        Integer maxPrice) {
}
