package com.vego.backend.booking;

import com.vego.backend.booking.dto.BookingResponse;
import com.vego.backend.booking.dto.CreateBookingRequest;
import com.vego.backend.common.ApiException;
import com.vego.backend.common.Phones;
import com.vego.backend.config.AppProperties;
import com.vego.backend.config.BookingProperties;
import com.vego.backend.home.HomeService;
import com.vego.backend.home.PromotionRepository;
import com.vego.backend.home.Trip;
import com.vego.backend.home.TripRepository;
import com.vego.backend.trip.SeatInventory;
import com.vego.backend.payment.Payment;
import com.vego.backend.payment.PaymentRepository;
import com.vego.backend.user.User;
import com.vego.backend.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;

@Service
public class BookingService {

    private static final Logger log = LoggerFactory.getLogger(BookingService.class);
    /** Không bán vé cho chuyến khởi hành trong vòng 30 phút tới. */
    private static final Duration MIN_LEAD_TIME = Duration.ofMinutes(30);
    private static final String CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final BookingRepository bookings;
    private final TripRepository trips;
    private final UserRepository users;
    private final PaymentRepository payments;
    private final PromotionRepository promotions;
    private final VoucherService vouchers;
    private final SeatInventory seats;
    private final Duration holdDuration;
    private final ZoneId zone;
    private final Clock clock;

    public BookingService(BookingRepository bookings, TripRepository trips, UserRepository users,
                          PaymentRepository payments, PromotionRepository promotions, VoucherService vouchers,
                          SeatInventory seats, BookingProperties bookingProps, AppProperties props, Clock clock) {
        this.bookings = bookings;
        this.trips = trips;
        this.users = users;
        this.payments = payments;
        this.promotions = promotions;
        this.vouchers = vouchers;
        this.seats = seats;
        this.holdDuration = bookingProps.holdDuration();
        this.zone = props.zone();
        this.clock = clock;
    }

    /** Giữ ghế: tạo đơn HOLDING, hết hạn sau holdDuration nếu chưa thanh toán. */
    @Transactional
    public BookingResponse create(long userId, CreateBookingRequest req) {
        Instant now = clock.instant();
        User user = users.findById(userId)
                .orElseThrow(() -> ApiException.unauthorized("USER_NOT_FOUND", "Tài khoản không còn tồn tại"));
        Trip trip = trips.findActiveWithDetails(req.tripId().trim())
                .orElseThrow(() -> ApiException.notFound("TRIP_NOT_FOUND", "Không tìm thấy chuyến xe"));

        LocalDateTime departure = req.travelDate().atTime(trip.getDepartTime());
        if (departure.isBefore(LocalDateTime.now(clock.withZone(zone)).plus(MIN_LEAD_TIME))) {
            throw ApiException.field("TRIP_DEPARTED", "travelDate",
                    "Chuyến này đã khởi hành hoặc sắp khởi hành, vui lòng chọn chuyến khác");
        }
        if (req.travelDate().isAfter(LocalDate.now(clock.withZone(zone)).plusDays(60))) {
            throw ApiException.field("DATE_TOO_FAR", "travelDate", "Chỉ đặt vé trước tối đa 60 ngày");
        }
        String phone = Phones.normalize(req.passengerPhone());
        if (phone == null) {
            throw ApiException.field("INVALID_PHONE", "passengerPhone", "Số điện thoại cần 10 chữ số");
        }
        String seat = normalizeSeat(req.seat());
        if (!seats.exists(trip, seat)) {
            throw ApiException.field("INVALID_SEAT", "seat", "Xe này không có ghế " + seat);
        }
        if (seats.presold(trip, req.travelDate()).contains(seat)) {
            throw seatTaken(seat);
        }

        // Đơn cũ hết hạn được giải phóng; đơn đang giữ khác của chính người này bị hủy
        bookings.expireHolds(now);
        bookings.cancelHoldsOfUser(userId, now);
        if (bookings.seatTaken(trip.getId(), req.travelDate(), seat, -1L, now)) {
            throw seatTaken(seat);
        }

        Booking b = new Booking();
        b.setCode(newCode(req.travelDate()));
        b.setUser(user);
        b.setTrip(trip);
        b.setTravelDate(req.travelDate());
        b.setSeatCode(seat);
        b.setPassengerName(req.passengerName().trim().replaceAll("\\s+", " "));
        b.setPassengerPhone(phone);
        b.setPickupLabel(orDefault(req.pickupLabel(), trip.getPickupStation().getName()));
        b.setDropoffLabel(orDefault(req.dropoffLabel(), trip.getDropoffStation().getName()));
        b.setFare(trip.getPrice());
        b.setHoldExpiresAt(now.plus(holdDuration));
        try {
            bookings.saveAndFlush(b);
        } catch (DataIntegrityViolationException e) {
            // Hai người bấm giữ cùng một ghế cùng lúc: chỉ một người thắng (chỉ mục duy nhất trong CSDL)
            throw seatTaken(seat);
        }
        log.info("Giữ ghế {} chuyến {} ngày {} -> đơn {}", seat, trip.getId(), req.travelDate(), b.getCode());
        return toResponse(b);
    }

    @Transactional
    public BookingResponse get(long userId, String code) {
        return toResponse(refresh(load(userId, code)));
    }

    @Transactional
    public List<BookingResponse> list(long userId) {
        bookings.expireHolds(clock.instant());
        return bookings.findByUser(userId).stream().map(this::toResponse).toList();
    }

    @Transactional
    public BookingResponse applyVoucher(long userId, String code, String voucherCode) {
        Booking b = requireHolding(load(userId, code));
        VoucherService.Applied applied = vouchers.evaluate(b, voucherCode);
        b.applyDiscount(applied.code(), applied.discount());
        return toResponse(bookings.saveAndFlush(b));
    }

    @Transactional
    public BookingResponse removeVoucher(long userId, String code) {
        Booking b = requireHolding(load(userId, code));
        b.applyDiscount(null, 0);
        return toResponse(bookings.saveAndFlush(b));
    }

    /** Hủy giữ chỗ (người dùng bấm "Hủy giữ chỗ" hoặc rời luồng). Đơn đã thanh toán không hủy ở đây. */
    @Transactional
    public BookingResponse cancel(long userId, String code) {
        Booking b = refresh(load(userId, code));
        if (b.getStatus() == Booking.Status.PAID) {
            throw new ApiException(HttpStatus.CONFLICT, "ALREADY_PAID", "Vé đã thanh toán, không thể hủy giữ chỗ");
        }
        if (b.getStatus() == Booking.Status.HOLDING) {
            b.setStatus(Booking.Status.CANCELLED);
            bookings.saveAndFlush(b);
        }
        return toResponse(b);
    }

    /** Giải phóng ghế của các đơn giữ quá hạn, chạy mỗi 30 giây. */
    @Scheduled(fixedDelay = 30_000, initialDelay = 30_000)
    @Transactional
    public void expireHolds() {
        int n = bookings.expireHolds(clock.instant());
        if (n > 0) log.info("Hết hạn giữ chỗ: {} đơn", n);
    }

    // ---------- Dùng chung ----------

    Booking load(long userId, String code) {
        Booking b = bookings.findByCodeWithTrip(code.trim().toUpperCase(Locale.ROOT))
                .orElseThrow(() -> ApiException.notFound("BOOKING_NOT_FOUND", "Không tìm thấy đơn đặt vé"));
        if (!b.getUser().getId().equals(userId)) {
            // Không tiết lộ đơn của người khác có tồn tại hay không
            throw ApiException.notFound("BOOKING_NOT_FOUND", "Không tìm thấy đơn đặt vé");
        }
        return b;
    }

    /** Đơn đang giữ mà đã quá giờ -> chuyển EXPIRED ngay khi được đọc. */
    private Booking refresh(Booking b) {
        if (b.getStatus() == Booking.Status.HOLDING && !b.isHoldingAt(clock.instant())) {
            b.setStatus(Booking.Status.EXPIRED);
            bookings.saveAndFlush(b);
        }
        return b;
    }

    public Booking requireHolding(Booking b) {
        refresh(b);
        switch (b.getStatus()) {
            case HOLDING -> { return b; }
            case PAID -> throw new ApiException(HttpStatus.CONFLICT, "ALREADY_PAID", "Vé này đã được thanh toán");
            case EXPIRED -> throw new ApiException(HttpStatus.GONE, "HOLD_EXPIRED",
                    "Đã hết thời gian giữ ghế, vui lòng chọn lại ghế");
            default -> throw new ApiException(HttpStatus.GONE, "BOOKING_CANCELLED", "Đơn đặt vé đã bị hủy");
        }
    }

    public BookingResponse toResponse(Booking b) {
        Instant now = clock.instant();
        long remaining = b.getStatus() == Booking.Status.HOLDING
                ? Math.max(0, Duration.between(now, b.getHoldExpiresAt()).toSeconds()) : 0;
        String voucherTitle = vouchers.titleOf(b.getVoucherCode());
        BookingResponse.LastPayment last = b.getId() == null ? null
                : payments.findFirstByBookingIdOrderByCreatedAtDesc(b.getId())
                .map(p -> new BookingResponse.LastPayment(p.getTxnRef(), p.getProvider().name(),
                        p.getMethod().name(), p.getStatus().name(), p.getMessage(), p.getCreatedAt()))
                .orElse(null);
        return new BookingResponse(b.getCode(), b.getStatus().name(),
                HomeService.toTrip(b.getTrip(), b.getTravelDate()), b.getTravelDate(), b.getSeatCode(),
                b.getPassengerName(), b.getPassengerPhone(), b.getPickupLabel(), b.getDropoffLabel(),
                b.getFare(), b.getDiscount(), b.getTotal(), b.getVoucherCode(), voucherTitle,
                b.getPaymentMethod(), b.getHoldExpiresAt(), remaining, b.getPaidAt(), b.getCreatedAt(), last);
    }

    private String newCode(LocalDate date) {
        String prefix = String.format(Locale.ROOT, "VG%02d%02d%02d",
                date.getDayOfMonth(), date.getMonthValue(), date.getYear() % 100);
        for (int attempt = 0; attempt < 10; attempt++) {
            StringBuilder sb = new StringBuilder(prefix);
            for (int i = 0; i < 4; i++) sb.append(CODE_CHARS.charAt(RANDOM.nextInt(CODE_CHARS.length())));
            String code = sb.toString();
            if (!bookings.existsByCode(code)) return code;
        }
        throw new IllegalStateException("Không sinh được mã vé");
    }

    /** "a5" / "A05" -> "A05". */
    static String normalizeSeat(String raw) {
        String s = raw.trim().toUpperCase(Locale.ROOT);
        return s.length() == 2 ? s.charAt(0) + "0" + s.charAt(1) : s;
    }

    private static ApiException seatTaken(String seat) {
        return ApiException.conflict("SEAT_TAKEN", "seat", "Ghế " + seat + " vừa có người giữ, vui lòng chọn ghế khác");
    }

    private static String orDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
