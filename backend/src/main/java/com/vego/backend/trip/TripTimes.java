package com.vego.backend.trip;

import com.vego.backend.home.Trip;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/** Giờ khởi hành / tới nơi thật của một chuyến chạy vào một ngày (theo giờ Việt Nam). */
public final class TripTimes {

    private TripTimes() { }

    public static Instant departure(Trip t, LocalDate date, ZoneId zone) {
        return date.atTime(t.getDepartTime()).atZone(zone).toInstant();
    }

    public static Instant arrival(Trip t, LocalDate date, ZoneId zone) {
        return departure(t, date, zone).plusSeconds(t.getDurationMinutes() * 60L);
    }

    public static int departMinutes(Trip t) {
        return t.getDepartTime().getHour() * 60 + t.getDepartTime().getMinute();
    }

    /** 0 = sáng (trước 12h), 1 = chiều (12–18h), 2 = tối (từ 18h). */
    public static int dayPart(int minutes) {
        if (minutes < 12 * 60) return 0;
        if (minutes < 18 * 60) return 1;
        return 2;
    }

    public static String dayPartName(int minutes) {
        return switch (dayPart(minutes)) {
            case 0 -> "buổi sáng";
            case 1 -> "buổi chiều";
            default -> "buổi tối";
        };
    }
}
