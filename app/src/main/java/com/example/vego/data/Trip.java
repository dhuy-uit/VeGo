package com.example.vego.data;

/** Một chuyến xe (lấy từ REST API, xem {@link #from}). */
public class Trip {

    public enum BusType { LIMOUSINE, SLEEPER, SEATER }

    public final String id;
    public final String operator;
    public final String busName;
    public final BusType busType;
    public final double rating;
    /** Giờ khởi hành tính bằng phút trong ngày, vd 22:00 = 1320. */
    public final int departMinutes;
    public final int durationMinutes;
    public final int price;
    public final int seatsLeft;
    public final String origin;
    public final String destination;
    public final String pickupStation;
    public final String pickupAddress;
    public final String dropoffStation;
    public final String dropoffAddress;

    public Trip(String id, String operator, String busName, BusType busType, double rating,
                int departMinutes, int durationMinutes, int price, int seatsLeft,
                String origin, String destination,
                String pickupStation, String pickupAddress,
                String dropoffStation, String dropoffAddress) {
        this.id = id;
        this.operator = operator;
        this.busName = busName;
        this.busType = busType;
        this.rating = rating;
        this.departMinutes = departMinutes;
        this.durationMinutes = durationMinutes;
        this.price = price;
        this.seatsLeft = seatsLeft;
        this.origin = origin;
        this.destination = destination;
        this.pickupStation = pickupStation;
        this.pickupAddress = pickupAddress;
        this.dropoffStation = dropoffStation;
        this.dropoffAddress = dropoffAddress;
    }

    public int arriveMinutes() {
        return (departMinutes + durationMinutes) % (24 * 60);
    }

    /** Số ngày cộng thêm khi tới nơi (vd đi 22:00, tới 05:00 hôm sau = 1). */
    public int arriveDayOffset() {
        return (departMinutes + durationMinutes) / (24 * 60);
    }

    public String departTime() {
        return Format.time(departMinutes);
    }

    public String arriveTime() {
        return Format.time(arriveMinutes());
    }

    public String durationText() {
        int h = durationMinutes / 60;
        int m = durationMinutes % 60;
        return m == 0 ? h + " giờ" : h + " giờ " + m + " phút";
    }

    public String ratingText() {
        return Format.rating(rating) + " ★";
    }

    /** Đổi dữ liệu chuyến từ API sang lớp Trip mà các màn hình đang dùng. */
    public static Trip from(com.example.vego.data.api.ApiModels.TripDto t) {
        BusType type;
        try {
            type = BusType.valueOf(t.busType);
        } catch (RuntimeException e) {
            type = BusType.SLEEPER;
        }
        return new Trip(t.id, t.operator, t.busName, type, t.rating, t.departMinutes, t.durationMinutes,
                t.price, t.seatsLeft, t.origin, t.destination,
                t.pickupStation, t.pickupAddress, t.dropoffStation, t.dropoffAddress);
    }
}
