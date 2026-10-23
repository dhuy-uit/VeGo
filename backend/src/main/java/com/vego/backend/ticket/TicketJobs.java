package com.vego.backend.ticket;

import com.vego.backend.booking.Booking;
import com.vego.backend.booking.BookingRepository;
import com.vego.backend.config.AppProperties;
import com.vego.backend.notification.Notification;
import com.vego.backend.notification.NotificationService;
import com.vego.backend.trip.TripTimes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Thông báo tự động (chạy mỗi 5 phút):
 * <ul>
 *   <li>Nhắc lịch khi còn dưới 3 giờ nữa xe chạy (hẹn có mặt trước 30 phút).</li>
 *   <li>Mời đánh giá nhà xe khi chuyến đã tới nơi.</li>
 * </ul>
 */
@Component
public class TicketJobs {

    private static final Logger log = LoggerFactory.getLogger(TicketJobs.class);
    static final Duration REMIND_BEFORE = Duration.ofHours(3);
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private final BookingRepository bookings;
    private final NotificationService notifications;
    private final ZoneId zone;
    private final Clock clock;

    public TicketJobs(BookingRepository bookings, NotificationService notifications, AppProperties props, Clock clock) {
        this.bookings = bookings;
        this.notifications = notifications;
        this.zone = props.zone();
        this.clock = clock;
    }

    @Scheduled(fixedDelay = 300_000, initialDelay = 60_000)
    @Transactional
    public void run() {
        Instant now = clock.instant();
        LocalDate today = LocalDate.now(clock.withZone(zone));
        int reminders = 0, invites = 0;
        for (Booking b : bookings.findPaidBetween(today.minusDays(3), today.plusDays(1))) {
            Instant departure = TripTimes.departure(b.getTrip(), b.getTravelDate(), zone);
            Instant arrival = TripTimes.arrival(b.getTrip(), b.getTravelDate(), zone);
            if (!b.isReminderSent() && departure.isAfter(now) && !departure.isAfter(now.plus(REMIND_BEFORE))) {
                remind(b, departure);
                b.setReminderSent(true);
                reminders++;
            } else if (!b.isReminderSent() && !departure.isAfter(now)) {
                b.setReminderSent(true);   // đã qua giờ đi: không nhắc nữa
            }
            if (!b.isReviewInvited() && !arrival.isAfter(now)) {
                invite(b);
                b.setReviewInvited(true);
                invites++;
            }
        }
        if (reminders + invites > 0) log.info("Thông báo tự động: {} nhắc lịch, {} mời đánh giá", reminders, invites);
    }

    private void remind(Booking b, Instant departure) {
        var depart = departure.atZone(zone);
        var meet = depart.minusMinutes(30);
        String place = b.getPickupLabel().startsWith("Xe trung chuyển")
                ? "Nhân viên sẽ gọi số " + b.getPassengerPhone() + " để hẹn điểm đón."
                : "Vui lòng có mặt tại " + b.getPickupLabel() + " lúc " + TIME.format(meet) + ".";
        notifications.push(b.getUser().getId(), Notification.Kind.REMINDER,
                "Nhắc lịch: xe chạy lúc " + TIME.format(depart),
                "Chuyến " + b.getTrip().getRoute().getOriginCity() + " → " + b.getTrip().getRoute().getDestinationCity()
                        + " ngày " + DAY.format(b.getTravelDate()) + ", ghế " + b.getSeatCode() + ". " + place,
                b.getCode());
    }

    private void invite(Booking b) {
        notifications.push(b.getUser().getId(), Notification.Kind.REVIEW, "Cảm ơn bạn đã đồng hành",
                "Hành trình ngày " + DAY.format(b.getTravelDate()) + " cùng " + b.getTrip().getOperator().getName()
                        + " đã hoàn thành. Chia sẻ trải nghiệm để giúp nhà xe tốt hơn.",
                b.getCode());
    }
}
