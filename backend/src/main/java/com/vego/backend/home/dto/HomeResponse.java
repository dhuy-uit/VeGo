package com.vego.backend.home.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * Dữ liệu cho màn Trang chủ, gom trong 1 lần gọi để app tải nhanh.
 * greetingName = null khi khách chưa đăng nhập.
 */
public record HomeResponse(
        boolean loggedIn,
        String greetingName,
        SearchDefaults searchDefaults,
        List<PromotionResponse> promotions,
        List<PopularRouteResponse> popularRoutes,
        SuggestedTrip suggestedTrip) {

    public record SearchDefaults(String origin, String destination, LocalDate date) { }

    /** reason: lý do gợi ý, vd "Khởi hành sớm nhất · Còn 12 ghế". */
    public record SuggestedTrip(String reason, TripResponse trip) { }
}
