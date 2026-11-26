package com.vego.backend.support;

import com.vego.backend.booking.BookingService;
import com.vego.backend.booking.dto.BookingResponse;
import com.vego.backend.booking.dto.CreateBookingRequest;
import com.vego.backend.common.ApiException;
import com.vego.backend.support.SupportDtos.Action;
import com.vego.backend.support.SupportDtos.ActionResult;
import com.vego.backend.ticket.TicketEvents;
import com.vego.backend.ticket.TicketService;
import com.vego.backend.ticket.dto.ExchangeRequest;
import com.vego.backend.ticket.dto.TicketResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thao tác trợ lý đề xuất (đặt vé, hủy vé, đổi vé) chờ khách xác nhận.
 * Mô hình KHÔNG tự thực hiện được gì: nó chỉ tạo đề xuất và nhận về một token; app hiện nút Xác nhận, khách bấm thì
 * app gọi confirm với token đó. Token gắn với đúng một người dùng, dùng một lần, hết hạn sau {@link #TTL}.
 * Mọi quy tắc nghiệp vụ (còn ghế, hạn đổi, hoàn tiền...) vẫn do BookingService / TicketService kiểm tra khi thực hiện.
 */
@Component
public class SupportActions {

    public enum Kind { BOOK, CANCEL, EXCHANGE }

    static final Duration TTL = Duration.ofMinutes(10);
    private static final int MAX_PER_USER = 5;

    private record Pending(long userId, Kind kind, String ticketCode, String tripId, LocalDate date, String seat,
                           String passengerName, String passengerPhone, Instant until) { }

    private final BookingService bookings;
    private final TicketService tickets;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();
    private final Map<String, Pending> pending = new ConcurrentHashMap<>();

    public SupportActions(BookingService bookings, TicketService tickets, Clock clock) {
        this.bookings = bookings;
        this.tickets = tickets;
        this.clock = clock;
    }

    public Action proposeBooking(long userId, String tripId, LocalDate date, String seat, String name, String phone,
                                 String summary) {
        return store(new Pending(userId, Kind.BOOK, null, tripId, date, seat, name, phone, expiry()),
                "Đặt vé", summary, "Xác nhận giữ ghế");
    }

    public Action proposeCancel(long userId, String ticketCode, String summary) {
        return store(new Pending(userId, Kind.CANCEL, ticketCode, null, null, null, null, null, expiry()),
                "Hủy vé " + ticketCode, summary, "Xác nhận hủy vé");
    }

    public Action proposeExchange(long userId, String ticketCode, String tripId, LocalDate date, String seat,
                                  String summary) {
        return store(new Pending(userId, Kind.EXCHANGE, ticketCode, tripId, date, seat, null, null, expiry()),
                "Đổi vé " + ticketCode, summary, "Xác nhận đổi vé");
    }

    /** Khách bấm Xác nhận: thực hiện đúng thao tác đã đề xuất (token dùng một lần). */
    public ActionResult confirm(long userId, String token) {
        Pending p = take(userId, token);
        return switch (p.kind()) {
            case BOOK -> {
                BookingResponse b = bookings.create(userId, new CreateBookingRequest(p.tripId(), p.date(), p.seat(),
                        p.passengerName(), p.passengerPhone(), null, null));
                yield new ActionResult("BOOK", "Đã giữ ghế " + b.seat() + " trong "
                        + Math.max(1, b.holdRemainingSeconds() / 60) + " phút. Bạn thanh toán qua VNPay để hoàn tất đặt vé nhé.",
                        b, null);
            }
            case CANCEL -> {
                TicketResponse t = tickets.cancel(userId, p.ticketCode());
                yield new ActionResult("CANCEL", "Đã hủy vé " + t.code() + ". Số tiền hoàn: "
                        + TicketEvents.money(t.refundAmount()) + " (" + t.refundPercent() + "%).", null, t);
            }
            case EXCHANGE -> {
                TicketResponse t = tickets.exchange(userId, p.ticketCode(),
                        new ExchangeRequest(p.tripId(), p.date(), p.seat(), null, null));
                yield new ActionResult("EXCHANGE", "Đã đổi vé " + t.code() + ". Mã QR mới đã có trong Vé điện tử.",
                        null, t);
            }
        };
    }

    /** Khách bấm Bỏ qua. */
    public void dismiss(long userId, String token) {
        Pending p = pending.get(token);
        if (p != null && p.userId() == userId) pending.remove(token);
    }

    // ---------- nội bộ ----------

    private Instant expiry() {
        return clock.instant().plus(TTL);
    }

    private Action store(Pending p, String title, String summary, String confirmLabel) {
        Instant now = clock.instant();
        pending.values().removeIf(x -> x.until().isBefore(now));
        // Mỗi người chỉ giữ vài đề xuất chưa xác nhận; đề xuất mới thay các đề xuất cũ cùng loại
        pending.entrySet().removeIf(e -> e.getValue().userId() == p.userId() && e.getValue().kind() == p.kind());
        if (pending.values().stream().filter(x -> x.userId() == p.userId()).count() >= MAX_PER_USER) {
            pending.entrySet().stream().filter(e -> e.getValue().userId() == p.userId()).findFirst()
                    .ifPresent(e -> pending.remove(e.getKey()));
        }
        byte[] bytes = new byte[18];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        pending.put(token, p);
        return new Action(token, p.kind().name(), title, summary, confirmLabel);
    }

    private Pending take(long userId, String token) {
        // Chỉ chủ đề xuất mới "tiêu thụ" được token: người khác thử token này thì bị từ chối mà không làm mất đề xuất.
        Pending p = token == null ? null : pending.get(token);
        // remove(key, value) là nguyên tử: hai yêu cầu xác nhận cùng lúc thì chỉ một yêu cầu thắng
        if (p == null || p.userId() != userId || !pending.remove(token, p) || p.until().isBefore(clock.instant())) {
            throw new ApiException(HttpStatus.GONE, "ACTION_EXPIRED",
                    "Yêu cầu này đã hết hạn hoặc đã được xử lý. Bạn nhờ trợ lý làm lại giúp nhé.");
        }
        return p;
    }
}
