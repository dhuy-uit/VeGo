package com.vego.backend.home.dto;

import java.time.LocalDate;

public record PromotionResponse(
        String code,
        String badge,
        String title,
        String description,
        String note,
        Integer discountPercent,
        Integer discountAmount,
        Integer maxDiscount,
        String imageKey,
        String origin,
        String destination,
        LocalDate validUntil) {
}
