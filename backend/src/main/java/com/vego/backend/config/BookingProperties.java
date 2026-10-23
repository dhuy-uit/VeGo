package com.vego.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Cấu hình đặt vé ("app.booking").
 * presoldSeats: giả lập ghế đã bán qua kênh khác (quầy vé, nhà xe) theo cột trips.seats_left
 * để sơ đồ ghế giống thực tế; tắt trong test để chọn ghế cố định.
 */
@ConfigurationProperties(prefix = "app.booking")
public record BookingProperties(Duration holdDuration, Boolean presoldSeats) {

    public BookingProperties {
        if (holdDuration == null) holdDuration = Duration.ofMinutes(5);
        if (presoldSeats == null) presoldSeats = Boolean.TRUE;
    }
}
