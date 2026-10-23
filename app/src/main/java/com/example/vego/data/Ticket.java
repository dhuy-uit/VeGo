package com.example.vego.data;

import androidx.annotation.Nullable;

import com.example.vego.common.UserSession;
import com.example.vego.data.api.ApiModels;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/** Một vé đã đặt (Vé của tôi), dựng từ dữ liệu GET /api/v1/tickets. */
public class Ticket {

    public enum Status { UPCOMING, COMPLETED, CANCELLED }

    public final String code;
    public final String tripId;
    public final String operator;
    public final String busName;
    public final double tripRating;
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
    /** Số tiền đã trả (sau mã giảm giá). */
    public final int price;
    public final int fare;
    public final int discount;
    public final String paymentMethod;
    public final Status status;
    /** Vé đã hủy: số tiền đã hoàn. Vé sắp đi: số tiền sẽ hoàn nếu hủy lúc này. */
    public final int refundAmount;
    public final int refundPercent;
    public final boolean cancellable;
    public final boolean canReview;
    /** Số sao đã đánh giá, 0 = chưa đánh giá. */
    public final int rating;
    public final List<String> reviewTags;
    public final int coinsEarned;
    /** Nội dung mã QR (có chữ ký của server). */
    public final String qrData;

    public Ticket(ApiModels.Ticket t) {
        code = t.code;
        tripId = t.trip.id;
        operator = t.trip.operator;
        busName = t.trip.busName;
        tripRating = t.trip.rating;
        origin = t.trip.origin;
        destination = t.trip.destination;
        departMinutes = t.trip.departMinutes;
        durationMinutes = t.trip.durationMinutes;
        Calendar d = UserSession.parseIsoDate(t.travelDate);
        date = d != null ? d : Calendar.getInstance();
        seat = t.seat;
        passengerName = t.passengerName;
        passengerPhone = UserSession.formatPhone(t.passengerPhone);
        pickup = t.pickupLabel;
        dropoff = t.dropoffLabel;
        price = t.total;
        fare = t.fare;
        discount = t.discount;
        paymentMethod = t.paymentMethodLabel != null ? t.paymentMethodLabel : "";
        status = parseStatus(t.status);
        refundAmount = t.refundAmount;
        refundPercent = t.refundPercent;
        cancellable = t.cancellable;
        canReview = t.canReview;
        rating = t.review != null ? t.review.rating : 0;
        reviewTags = t.review != null && t.review.tags != null ? new ArrayList<>(t.review.tags) : new ArrayList<>();
        coinsEarned = t.coinsEarned;
        qrData = t.qrData != null ? t.qrData : "VEGO|" + code + "|" + seat;
    }

    private static Status parseStatus(@Nullable String s) {
        try {
            return s == null ? Status.UPCOMING : Status.valueOf(s);
        } catch (IllegalArgumentException e) {
            return Status.UPCOMING;
        }
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
