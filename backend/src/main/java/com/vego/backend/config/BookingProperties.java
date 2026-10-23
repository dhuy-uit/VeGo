package com.vego.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/** Cấu hình đặt vé ("app.booking"). */
@ConfigurationProperties(prefix = "app.booking")
public record BookingProperties(Duration holdDuration) {

    public BookingProperties {
        if (holdDuration == null) holdDuration = Duration.ofMinutes(5);
    }
}
