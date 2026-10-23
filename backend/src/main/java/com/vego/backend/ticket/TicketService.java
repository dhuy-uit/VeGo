package com.vego.backend.ticket;

import com.vego.backend.booking.Booking;
import com.vego.backend.booking.BookingRepository;
import com.vego.backend.common.ApiException;
import com.vego.backend.config.AppProperties;
import com.vego.backend.home.HomeService;
import com.vego.backend.notification.Notification;
import com.vego.backend.notification.NotificationService;
import com.vego.backend.ticket.dto.ReviewRequest;
import com.vego.backend.ticket.dto.TicketResponse;
import com.vego.backend.trip.TripTimes;
import com.vego.backend.wallet.CoinLedger;
import com.vego.backend.wallet.CoinTransaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Vé của tôi: danh sách vé đã thanh toán, hủy vé (hoàn tiền theo chính sách), đánh giá nhà xe.
 * Chính sách hoàn tiền: hủy trước giờ khởi hành từ 24 giờ hoàn 80%, sát giờ hơn hoàn 50%;
 * đã qua giờ khởi hành thì không hủy được.
 */
@Service
public class TicketService {

    private static final Logger log = LoggerFactory.getLogger(TicketService.class);
    public static final int REFUND_PERCENT_EARLY = 80;
    public static final int REFUND_PERCENT_LATE = 50;
    private static final Duration EARLY_CANCEL = Duration.ofHours(24);
    private static final Set<String> TAGS = Set.of("CLEAN", "ONTIME", "STAFF");
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM");

    private final BookingRepository bookings;
    private final ReviewRepository reviews;
    private final NotificationService notifications;
    private final CoinLedger ledger;
    private final TicketQr qr;
    private final ZoneId zone;
    private final Clock clock;

    public TicketService(BookingRepository bookings, ReviewRepository reviews, NotificationService notifications,
                         CoinLedger ledger, TicketQr qr, AppProperties props, Clock clock) {
        this.bookings = bookings;
        this.reviews = reviews;
        this.notifications = notifications;
        this.ledger = ledger;
        this.qr = qr;
        this.zone = props.zone();
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<TicketResponse> list(long userId) {
        List<Booking> list = bookings.findTicketsOfUser(userId);
        Map<Long, Review> byBooking = list.isEmpty() ? Map.of()
                : reviews.findByBookingIdIn(list.stream().map(Booking::getId).toList()).stream()
                .collect(Collectors.toMap(Review::getBookingId, Function.identity()));
        Instant now = clock.instant();
        return list.stream().map(b -> toResponse(b, byBooking.get(b.getId()), now)).toList();
    }

    @Transactional(readOnly = true)
    public TicketResponse get(long userId, String code) {
        Booking b = load(userId, code);
        return toResponse(b, reviews.findByBookingId(b.getId()).orElse(null), clock.instant());
    }

    @Transactional
    public TicketResponse cancel(long userId, String code) {
        Booking b = load(userId, code);
        bookings.lockById(b.getId());
        Instant now = clock.instant();
        if (b.isRefunded()) {
            throw new ApiException(HttpStatus.CONFLICT, "TICKET_CANCELLED", "Vé này đã được hủy trước đó");
        }
        Instant departure = TripTimes.departure(b.getTrip(), b.getTravelDate(), zone);
        if (!departure.isAfter(now)) {
            throw new ApiException(HttpStatus.CONFLICT, "TICKET_DEPARTED",
                    "Chuyến xe đã khởi hành, không thể hủy vé");
        }
        int refund = refundAmount(b, departure, now);
        b.setStatus(Booking.Status.CANCELLED);
        b.setCancelledAt(now);
        b.setRefundAmount(refund);
        bookings.saveAndFlush(b);

        long uid = b.getUser().getId();
        int revoked = b.getCoinsEarned() > 0
                ? ledger.debitUpTo(uid, CoinTransaction.Source.REFUND, b.getCoinsEarned(), "Thu hồi xu vé " + b.getCode(),
                b.getCode())
                : 0;
        String method = TicketEvents.methodLabel(b.getPaymentMethod());
        notifications.push(uid, Notification.Kind.TICKET, "Đã hủy vé " + b.getCode(),
                "Hoàn " + TicketEvents.money(refund) + " về " + method + " trong 3–5 ngày làm việc. Ghế "
                        + b.getSeatCode() + " đã được mở bán lại." + (revoked > 0 ? " Thu hồi " + revoked + " xu đã cộng." : ""),
                b.getCode());
        log.info("Hủy vé {}: hoàn {}đ ({}%)", b.getCode(), refund, refundPercent(departure, now));
        return toResponse(b, reviews.findByBookingId(b.getId()).orElse(null), now);
    }

    @Transactional
    public TicketResponse review(long userId, String code, ReviewRequest req) {
        Booking b = load(userId, code);
        Instant now = clock.instant();
        if (!"COMPLETED".equals(status(b, now))) {
            throw new ApiException(HttpStatus.CONFLICT, "REVIEW_NOT_ALLOWED",
                    "Chỉ đánh giá được sau khi chuyến xe đã khởi hành");
        }
        Set<String> tags = new LinkedHashSet<>();
        if (req.tags() != null) {
            for (String t : req.tags()) {
                String tag = t == null ? "" : t.trim().toUpperCase(Locale.ROOT);
                if (!TAGS.contains(tag)) {
                    throw ApiException.field("INVALID_TAG", "tags", "Nhãn đánh giá không hợp lệ: " + t);
                }
                tags.add(tag);
            }
        }
        Review r = reviews.findByBookingId(b.getId()).orElse(null);
        boolean first = r == null;
        if (first) r = new Review(b.getId(), userId, b.getTrip().getId(), b.getTrip().getOperator().getId());
        r.setRating(req.rating());
        r.setTags(tags.isEmpty() ? null : String.join(",", tags));
        r.setComment(req.comment() == null || req.comment().isBlank() ? null : req.comment().trim());
        reviews.saveAndFlush(r);
        if (first) {
            String operator = b.getTrip().getOperator().getName();
            notifications.push(userId, Notification.Kind.REVIEW, "Cảm ơn bạn đã đánh giá " + operator,
                    "Bạn đã chấm " + req.rating() + " sao cho chuyến ngày " + DAY.format(b.getTravelDate())
                            + ". Góp ý của bạn giúp nhà xe phục vụ tốt hơn.",
                    b.getCode());
        }
        return toResponse(b, r, now);
    }

    // ---------- Dùng chung ----------

    private Booking load(long userId, String code) {
        Booking b = bookings.findByCodeWithTrip(code.trim().toUpperCase(Locale.ROOT))
                .filter(x -> x.getUser().getId().equals(userId))
                .filter(x -> x.getPaidAt() != null)
                .filter(x -> x.getStatus() == Booking.Status.PAID || x.getStatus() == Booking.Status.CANCELLED)
                .orElseThrow(() -> ApiException.notFound("TICKET_NOT_FOUND", "Không tìm thấy vé"));
        return b;
    }

    String status(Booking b, Instant now) {
        if (b.isRefunded()) return "CANCELLED";
        return TripTimes.departure(b.getTrip(), b.getTravelDate(), zone).isAfter(now) ? "UPCOMING" : "COMPLETED";
    }

    static int refundPercent(Instant departure, Instant now) {
        return Duration.between(now, departure).compareTo(EARLY_CANCEL) >= 0 ? REFUND_PERCENT_EARLY : REFUND_PERCENT_LATE;
    }

    static int refundAmount(Booking b, Instant departure, Instant now) {
        return b.getTotal() * refundPercent(departure, now) / 100 / 1000 * 1000;
    }

    private TicketResponse toResponse(Booking b, Review review, Instant now) {
        String status = status(b, now);
        Instant departure = TripTimes.departure(b.getTrip(), b.getTravelDate(), zone);
        boolean cancellable = "UPCOMING".equals(status);
        int percent = cancellable ? refundPercent(departure, now) : 0;
        int refund = b.isRefunded() ? (b.getRefundAmount() == null ? 0 : b.getRefundAmount())
                : cancellable ? refundAmount(b, departure, now) : 0;
        if (b.isRefunded() && b.getTotal() > 0) percent = Math.round(refund * 100f / b.getTotal());
        TicketResponse.ReviewInfo reviewInfo = review == null ? null : new TicketResponse.ReviewInfo(review.getRating(),
                review.getTags() == null ? List.of() : Arrays.asList(review.getTags().split(",")),
                review.getComment(), review.getCreatedAt());
        return new TicketResponse(b.getCode(), status, HomeService.toTrip(b.getTrip(), b.getTravelDate()),
                b.getTravelDate(), b.getSeatCode(), b.getPassengerName(), b.getPassengerPhone(), b.getPickupLabel(),
                b.getDropoffLabel(), b.getFare(), b.getDiscount(), b.getTotal(), b.getVoucherCode(),
                b.getPaymentMethod(), TicketEvents.methodLabel(b.getPaymentMethod()), b.getPaidAt(), departure,
                TripTimes.arrival(b.getTrip(), b.getTravelDate(), zone), cancellable, percent, refund,
                b.getCancelledAt(), b.getCoinsEarned(), "COMPLETED".equals(status), reviewInfo, qr.payload(b));
    }
}
