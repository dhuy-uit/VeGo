package com.example.vego.data;

import java.util.Calendar;

/** Một vé đã đặt (Vé của tôi). */
public class Ticket {

    public enum Status { UPCOMING, COMPLETED, CANCELLED }

    public final String code;
    /** Chuyến gốc (null với vé cũ của tuyến không còn trong dữ liệu mẫu). */
    public final String tripId;
    public final String operator;
    public final String busName;
    public final String origin;
    public final String destination;
    public final int departMinutes;
    public final int durationMinutes;
    public final Calendar date;
    public final String seat;
    public final String passengerName;
    public final String passengerPhone;
    public final String pickup;
    public final String dropoff;
    public final int price;
    public final String paymentMethod;

    public Status status;
    public int refundAmount;
    /** Số sao đã đánh giá, 0 = chưa đánh giá. */
    public int rating;

    public Ticket(String code, String tripId, String operator, String busName,
                  String origin, String destination, int departMinutes, int durationMinutes,
                  Calendar date, String seat, String passengerName, String passengerPhone,
                  String pickup, String dropoff, int price, String paymentMethod, Status status) {
        this.code = code;
        this.tripId = tripId;
        this.operator = operator;
        this.busName = busName;
        this.origin = origin;
        this.destination = destination;
        this.departMinutes = departMinutes;
        this.durationMinutes = durationMinutes;
        this.date = (Calendar) date.clone();
        this.seat = seat;
        this.passengerName = passengerName;
        this.passengerPhone = passengerPhone;
        this.pickup = pickup;
        this.dropoff = dropoff;
        this.price = price;
        this.paymentMethod = paymentMethod;
        this.status = status;
    }

    public String route() {
        return origin + " → " + destination;
    }

    public String departTime() {
        return Format.time(departMinutes);
    }

    public String arriveTime() {
        return Format.time((departMinutes + durationMinutes) % (24 * 60));
    }

    public int arriveDayOffset() {
        return (departMinutes + durationMinutes) / (24 * 60);
    }

    /** Thời điểm khởi hành (mili giây). */
    public long departAtMillis() {
        Calendar c = (Calendar) date.clone();
        c.set(Calendar.HOUR_OF_DAY, departMinutes / 60);
        c.set(Calendar.MINUTE, departMinutes % 60);
        c.set(Calendar.SECOND, 0);
        return c.getTimeInMillis();
    }

    /** "Limousine giường nằm 34 chỗ" -> "Limousine 34 chỗ" cho dòng mô tả ngắn. */
    public String shortBusName() {
        return busName.replace(" giường nằm", "");
    }
}
