package com.vego.backend.seed;

import com.vego.backend.booking.Booking;
import com.vego.backend.booking.BookingRepository;
import com.vego.backend.config.AppProperties;
import com.vego.backend.home.Trip;
import com.vego.backend.home.TripRepository;
import com.vego.backend.notification.Notification;
import com.vego.backend.notification.NotificationRepository;
import com.vego.backend.trip.SeatInventory;
import com.vego.backend.user.User;
import com.vego.backend.user.UserRepository;
import com.vego.backend.wallet.CheckIn;
import com.vego.backend.wallet.CheckInRepository;
import com.vego.backend.wallet.CoinTransaction;
import com.vego.backend.wallet.CoinTransactionRepository;
import com.vego.backend.wallet.GamePlay;
import com.vego.backend.wallet.GamePlayRepository;
import com.vego.backend.wallet.UserVoucher;
import com.vego.backend.wallet.UserVoucherRepository;
import com.vego.backend.wallet.VoucherOfferRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Lịch sử mẫu cho tài khoản demo Nguyễn Minh Anh (giống dữ liệu mẫu trong Figma): 1 vé đã đi (chưa đánh giá),
 * 1 vé đã hủy, 1 vé sắp đi, Ví xu 1.250 xu với lịch sử giao dịch, chuỗi điểm danh 3 ngày, 1 voucher và thông báo.
 * Chỉ chạy một lần: khi tài khoản demo chưa có giao dịch xu nào.
 */
@Component
@Order(2)
public class DemoHistorySeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoHistorySeeder.class);
    private static final String CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final AppProperties props;
    private final UserRepository users;
    private final TripRepository trips;
    private final BookingRepository bookings;
    private final SeatInventory seats;
    private final CoinTransactionRepository coinTxs;
    private final CheckInRepository checkIns;
    private final GamePlayRepository games;
    private final VoucherOfferRepository offers;
    private final UserVoucherRepository userVouchers;
    private final NotificationRepository notifications;
    private final Clock clock;
    private ZoneId zone;
    private LocalDate today;

    public DemoHistorySeeder(AppProperties props, UserRepository users, TripRepository trips,
                             BookingRepository bookings, SeatInventory seats, CoinTransactionRepository coinTxs,
                             CheckInRepository checkIns, GamePlayRepository games, VoucherOfferRepository offers,
                             UserVoucherRepository userVouchers, NotificationRepository notifications, Clock clock) {
        this.props = props;
        this.users = users;
        this.trips = trips;
        this.bookings = bookings;
        this.seats = seats;
        this.coinTxs = coinTxs;
        this.checkIns = checkIns;
        this.games = games;
        this.offers = offers;
        this.userVouchers = userVouchers;
        this.notifications = notifications;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!props.seedDemoUser()) return;
        User user = users.findByPhone("0901234567").orElse(null);
        if (user == null || coinTxs.countByUserId(user.getId()) > 0) return;
        Trip dalat = trips.findActiveWithDetails("PT2200").orElse(null);
        Trip nhatrang = trips.findActiveWithDetails("PT2130").orElse(null);
        if (dalat == null || nhatrang == null) return;

        zone = props.zone();
        today = LocalDate.now(clock.withZone(zone));
        long uid = user.getId();

        // ---------- Vé ----------
        Booking done = ticket(user, dalat, today.minusDays(33), "B08", at(-35, 14, 5));
        done.setCoinsEarned(320);
        done.setReminderSent(true);
        done.setReviewInvited(true);

        Booking cancelled = ticket(user, nhatrang, today.minusDays(21), "A03", at(-25, 19, 40));
        cancelled.setStatus(Booking.Status.CANCELLED);
        cancelled.setCancelledAt(at(-23, 10, 15));
        cancelled.setRefundAmount(224_000);
        cancelled.setReminderSent(true);
        cancelled.setReviewInvited(true);

        LocalDate upcomingDate = today.plusDays(3);
        Booking upcoming = ticket(user, dalat, upcomingDate, freeSeat(dalat, upcomingDate), clock.instant().minusSeconds(3600));
        upcoming.setCoinsEarned(320);
        bookings.saveAll(List.of(done, cancelled, upcoming));

        // ---------- Ví xu (tổng 1.250 xu) ----------
        coin(uid, CoinTransaction.Source.WELCOME, 500, "Quà chào mừng thành viên mới", null, at(-40, 9, 0));
        coin(uid, CoinTransaction.Source.BOOKING, 320, "Đặt vé " + done.getCode(), done.getCode(), at(-35, 14, 6));
        coin(uid, CoinTransaction.Source.BOOKING, 280, "Đặt vé " + cancelled.getCode(), cancelled.getCode(), at(-25, 19, 41));
        coin(uid, CoinTransaction.Source.REFUND, -280, "Thu hồi xu vé " + cancelled.getCode(), cancelled.getCode(), at(-23, 10, 15));
        game(uid, -9, 1050, 100);
        game(uid, -8, 700, 70);
        game(uid, -7, 1060, 100);
        game(uid, -5, 1000, 100);
        game(uid, -4, 1000, 100);

        String voucherCode = "XU20K-" + random(4);
        offers.findById("V20").ifPresent(o -> userVouchers.save(
                new UserVoucher(voucherCode, uid, o, at(-3, 9, 10), at(-3, 9, 10).plus(Duration.ofDays(30)))));
        coin(uid, CoinTransaction.Source.VOUCHER, -500, "Voucher giảm 20.000đ", voucherCode, at(-3, 9, 10));
        for (int d = 3; d >= 1; d--) {
            Instant when = at(-d, 8, 15 + d);
            checkIns.save(new CheckIn(uid, today.minusDays(d), 4 - d, 4 - d, 20, when));
            coin(uid, CoinTransaction.Source.CHECK_IN, 20, "Điểm danh ngày " + (4 - d) + "/7", null, when);
        }
        game(uid, -2, 800, 80);
        coin(uid, CoinTransaction.Source.BOOKING, 320, "Đặt vé " + upcoming.getCode(), upcoming.getCode(),
                upcoming.getPaidAt());

        // ---------- Thông báo ----------
        note(uid, Notification.Kind.PROMO, "Chào mừng bạn đến với VéGo!",
                "Tặng bạn 500 xu trong Ví xu và mã CHAOBANMOI giảm 50.000đ cho chuyến đầu tiên.", null, at(-40, 9, 0), true);
        note(uid, Notification.Kind.TICKET, "Vé của bạn đã được xác nhận",
                "Mã vé " + done.getCode() + " · Ghế B08. Thanh toán MoMo thành công: 320.000đ.", done.getCode(),
                at(-35, 14, 6), true);
        note(uid, Notification.Kind.TICKET, "Đã hủy vé " + cancelled.getCode(),
                "Hoàn 224.000đ về MoMo trong 3–5 ngày làm việc. Ghế A03 đã được mở bán lại.", cancelled.getCode(),
                at(-23, 10, 15), true);
        note(uid, Notification.Kind.REVIEW, "Cảm ơn bạn đã đồng hành",
                "Hành trình ngày " + dm(done.getTravelDate()) + " cùng Phương Trang đã hoàn thành. "
                        + "Chia sẻ trải nghiệm để giúp nhà xe tốt hơn.", done.getCode(), at(-32, 9, 0), true);
        note(uid, Notification.Kind.PROMO, "Mùa thu, lên Đà Lạt thôi!",
                "Giảm 15%, tối đa 50.000đ tuyến TP. Hồ Chí Minh – Đà Lạt với mã VEGIN15.", null, at(-1, 8, 30), true);
        note(uid, Notification.Kind.TICKET, "Vé của bạn đã được xác nhận",
                "Mã vé " + upcoming.getCode() + " · Ghế " + upcoming.getSeatCode()
                        + ". Thanh toán MoMo thành công: 320.000đ. Bạn được cộng 320 xu.",
                upcoming.getCode(), upcoming.getPaidAt(), false);

        log.info("Đã tạo lịch sử mẫu cho tài khoản demo: vé {}, {}, {}", done.getCode(), cancelled.getCode(),
                upcoming.getCode());
    }

    private Booking ticket(User user, Trip trip, LocalDate date, String seat, Instant paidAt) {
        Booking b = new Booking();
        b.setCode(String.format(Locale.ROOT, "VG%02d%02d%02d%s", date.getDayOfMonth(), date.getMonthValue(),
                date.getYear() % 100, random(4)));
        b.setUser(user);
        b.setTrip(trip);
        b.setTravelDate(date);
        b.setSeatCode(seat);
        b.setPassengerName(user.getFullName());
        b.setPassengerPhone(user.getPhone());
        b.setPickupLabel(trip.getPickupStation().getName());
        b.setDropoffLabel(trip.getDropoffStation().getName());
        b.setFare(trip.getPrice());
        b.setStatus(Booking.Status.PAID);
        b.setPaymentMethod("MOMO");
        b.setHoldExpiresAt(paidAt);
        b.setPaidAt(paidAt);
        return b;
    }

    private String freeSeat(Trip trip, LocalDate date) {
        Set<String> taken = seats.unavailable(trip, date);
        if (!taken.contains("A05")) return "A05";
        return seats.allSeats(trip).stream().filter(s -> !taken.contains(s)).findFirst().orElse("A01");
    }

    private void coin(long uid, CoinTransaction.Source source, int amount, String note, String ref, Instant at) {
        coinTxs.adjustBalance(uid, amount);
        coinTxs.save(new CoinTransaction(uid, source, amount, note, ref, at));
    }

    private void game(long uid, int dayOffset, int score, int coins) {
        Instant start = at(dayOffset, 20, 30);
        GamePlay g = new GamePlay(uid, today.plusDays(dayOffset), start);
        g.finish(start.plusSeconds(60), score, coins);
        games.save(g);
        coin(uid, CoinTransaction.Source.GAME, coins, "Khủng long phiêu lưu · " + score + " điểm", null,
                start.plusSeconds(60));
    }

    private void note(long uid, Notification.Kind kind, String title, String body, String code, Instant at,
                      boolean read) {
        Notification n = new Notification(uid, kind, title, body, code);
        n.setCreatedAt(at);
        if (read) n.setReadAt(at.plusSeconds(600));
        notifications.save(n);
    }

    private Instant at(int dayOffset, int hour, int minute) {
        return today.plusDays(dayOffset).atTime(LocalTime.of(hour, minute)).atZone(zone).toInstant();
    }

    private static String dm(LocalDate d) {
        return String.format(Locale.ROOT, "%02d/%02d", d.getDayOfMonth(), d.getMonthValue());
    }

    private static String random(int n) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) sb.append(CODE_CHARS.charAt(RANDOM.nextInt(CODE_CHARS.length())));
        return sb.toString();
    }
}
