package com.vego.backend.booking.dto;

import com.vego.backend.home.dto.TripResponse;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Đơn đặt vé. status: HOLDING (đang giữ ghế) | PAID | EXPIRED | CANCELLED.
 * lastPayment: lần thanh toán gần nhất (để app biết vừa thanh toán thành công hay thất bại).
 */
public record BookingResponse(
        String code,
        String status,
        TripResponse trip,
        LocalDate travelDate,
        String seat,
        String passengerName,
        String passengerPhone,
        String pickupLabel,
        String dropoffLabel,
        int fare,
        int discount,
        int total,
        String voucherCode,
        String voucherTitle,
        String paymentMethod,
        Instant holdExpiresAt,
        long holdRemainingSeconds,
        Instant paidAt,
        Instant createdAt,
        LastPayment lastPayment) {

    public record LastPayment(String txnRef, String provider, String method, String status, String message,
                              Instant createdAt) { }
}
