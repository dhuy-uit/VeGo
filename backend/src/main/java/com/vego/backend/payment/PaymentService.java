package com.vego.backend.payment;

import com.vego.backend.booking.Booking;
import com.vego.backend.booking.BookingRepository;
import com.vego.backend.booking.BookingService;
import com.vego.backend.common.ApiException;
import com.vego.backend.common.UrlBuilder;
import com.vego.backend.config.PaymentProperties;
import com.vego.backend.payment.dto.PaymentResponse;
import com.vego.backend.ticket.TicketEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;

/**
 * Tạo giao dịch thanh toán cho đơn đang giữ và ghi nhận kết quả từ cổng.
 * Kết quả chỉ được tin khi chữ ký hợp lệ (IPN / return URL) — không tin kết quả app tự báo.
 */
@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);
    private static final String REF_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final PaymentRepository payments;
    private final BookingRepository bookings;
    private final BookingService bookingService;
    private final VnPayGateway vnpay;
    private final MoMoGateway momo;
    private final PaymentProperties props;
    private final UrlBuilder urls;
    private final TicketEvents ticketEvents;
    private final Clock clock;

    public PaymentService(PaymentRepository payments, BookingRepository bookings, BookingService bookingService,
                          VnPayGateway vnpay, MoMoGateway momo, PaymentProperties props, UrlBuilder urls,
                          TicketEvents ticketEvents, Clock clock) {
        this.payments = payments;
        this.bookings = bookings;
        this.bookingService = bookingService;
        this.vnpay = vnpay;
        this.momo = momo;
        this.props = props;
        this.urls = urls;
        this.ticketEvents = ticketEvents;
        this.clock = clock;
    }

    @Transactional
    public PaymentResponse create(long userId, String bookingCode, String methodName, String clientIp) {
        Payment.Method method = Payment.Method.valueOf(methodName);
        Booking booking = bookings.findByCodeWithTrip(bookingCode.trim().toUpperCase(Locale.ROOT))
                .filter(b -> b.getUser().getId().equals(userId))
                .orElseThrow(() -> ApiException.notFound("BOOKING_NOT_FOUND", "Không tìm thấy đơn đặt vé"));
        bookingService.requireHolding(booking);

        Instant now = clock.instant();
        if (Duration.between(now, booking.getHoldExpiresAt()).toSeconds() < 20) {
            throw new ApiException(HttpStatus.GONE, "HOLD_EXPIRED", "Sắp hết thời gian giữ ghế, vui lòng chọn lại ghế");
        }

        Payment.Provider provider = providerFor(method);
        Payment payment = payments.save(new Payment(booking, newTxnRef(booking), provider, method, booking.getTotal()));
        String orderInfo = "Thanh toan ve VeGo " + booking.getCode();

        String payUrl = switch (provider) {
            case VNPAY -> vnpay.createPayUrl(payment, orderInfo,
                    urls.absolute("/api/v1/payments/vnpay/return"), clientIp, now, booking.getHoldExpiresAt());
            case MOMO -> momo.createPayUrl(payment, orderInfo,
                    urls.absolute("/api/v1/payments/momo/return"), urls.absolute("/api/v1/payments/momo/ipn"),
                    Duration.between(now, booking.getHoldExpiresAt()).toMinutes());
            case SIMULATED -> urls.absolute("/payment-sim.html?ref=" + payment.getTxnRef()
                    + "&method=" + method.name() + "&amount=" + payment.getAmount());
        };
        payment.setPayUrl(payUrl);
        log.info("Tạo thanh toán {} ({} / {}) cho đơn {}: {}đ", payment.getTxnRef(), method, provider,
                booking.getCode(), payment.getAmount());
        return new PaymentResponse(payment.getTxnRef(), booking.getCode(), provider.name(), method.name(),
                payment.getAmount(), payUrl, urls.absolute("/payment-result.html"), booking.getHoldExpiresAt());
    }

    /** Kết quả xử lý một thông báo từ cổng. */
    public enum Outcome { APPLIED, ALREADY_DONE, NOT_FOUND, AMOUNT_MISMATCH }

    public record Result(Outcome outcome, String bookingCode, boolean success) { }

    /**
     * Ghi nhận kết quả thanh toán (đã kiểm tra chữ ký trước khi gọi). Gọi lặp lại nhiều lần vẫn an toàn:
     * giao dịch đã có kết quả thì bỏ qua.
     */
    @Transactional
    public Result complete(String txnRef, long amount, boolean success, String providerTxnId,
                           String responseCode, String message) {
        Payment payment = payments.findByTxnRef(txnRef).orElse(null);
        if (payment == null) return new Result(Outcome.NOT_FOUND, null, false);
        Booking booking = bookings.lockById(payment.getBooking().getId()).orElseThrow();
        if (amount != payment.getAmount()) {
            log.warn("Sai số tiền giao dịch {}: nhận {} / cần {}", txnRef, amount, payment.getAmount());
            return new Result(Outcome.AMOUNT_MISMATCH, booking.getCode(), false);
        }
        if (payment.getStatus() != Payment.Status.PENDING) {
            return new Result(Outcome.ALREADY_DONE, booking.getCode(), payment.getStatus() == Payment.Status.SUCCESS);
        }

        payment.complete(success, providerTxnId, responseCode, message);
        if (success) {
            Instant now = clock.instant();
            boolean seatStillFree = booking.getStatus() == Booking.Status.HOLDING
                    || !bookings.seatTaken(booking.getTrip().getId(), booking.getTravelDate(),
                    booking.getSeatCode(), booking.getId(), now);
            if (booking.getStatus() != Booking.Status.PAID && seatStillFree) {
                booking.setStatus(Booking.Status.PAID);
                booking.setPaidAt(now);
                booking.setPaymentMethod(payment.getMethod().name());
                log.info("Đơn {} đã thanh toán qua {} ({})", booking.getCode(), payment.getProvider(), txnRef);
                // Dùng voucher Ví xu, cộng xu đặt vé, gửi thông báo "Vé đã được xác nhận"
                ticketEvents.onPaid(booking);
            } else if (booking.getStatus() != Booking.Status.PAID) {
                // Trả tiền sau khi ghế đã thuộc về người khác: cần hoàn tiền thủ công
                log.error("Đơn {} thanh toán thành công nhưng ghế đã bị giữ -> cần hoàn tiền giao dịch {}",
                        booking.getCode(), txnRef);
            }
        }
        return new Result(Outcome.APPLIED, booking.getCode(), success);
    }

    private Payment.Provider providerFor(Payment.Method method) {
        if (props.simulate()) return Payment.Provider.SIMULATED;
        return switch (method) {
            case MOMO -> momo.configured() ? Payment.Provider.MOMO : Payment.Provider.SIMULATED;
            case VNPAY, CARD -> vnpay.configured() ? Payment.Provider.VNPAY : Payment.Provider.SIMULATED;
        };
    }

    private String newTxnRef(Booking booking) {
        StringBuilder sb = new StringBuilder(booking.getCode()).append('P');
        for (int i = 0; i < 5; i++) sb.append(REF_CHARS.charAt(RANDOM.nextInt(REF_CHARS.length())));
        return sb.toString();
    }
}
