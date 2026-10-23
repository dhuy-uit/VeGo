package com.vego.backend.payment.dto;

import java.time.Instant;

/**
 * App mở payUrl (WebView). Khi trang chuyển tới địa chỉ bắt đầu bằng resultUrl,
 * app đóng WebView và gọi GET /api/v1/bookings/{code} để xem kết quả.
 */
public record PaymentResponse(
        String txnRef,
        String bookingCode,
        String provider,
        String method,
        int amount,
        String payUrl,
        String resultUrl,
        Instant expiresAt) {
}
