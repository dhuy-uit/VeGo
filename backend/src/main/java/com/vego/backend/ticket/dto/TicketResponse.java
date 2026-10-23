package com.vego.backend.ticket.dto;

import com.vego.backend.home.dto.TripResponse;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Một vé đã thanh toán (màn Vé của tôi / Vé điện tử).
 * status: UPCOMING (sắp đi) | COMPLETED (đã đi) | CANCELLED (đã hủy, có hoàn tiền).
 * cancellable + refundPercent + refundAmount: nếu hủy ngay lúc này thì hoàn bao nhiêu
 * (vé đã hủy thì refundAmount là số đã hoàn).
 * qrData: nội dung mã QR có chữ ký để nhà xe soát vé.
 */
public record TicketResponse(
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
        String paymentMethod,
        String paymentMethodLabel,
        Instant paidAt,
        Instant departureAt,
        Instant arrivalAt,
        boolean cancellable,
        int refundPercent,
        int refundAmount,
        Instant cancelledAt,
        int coinsEarned,
        boolean canReview,
        ReviewInfo review,
        String qrData) {

    public record ReviewInfo(int rating, List<String> tags, String comment, Instant createdAt) { }
}
