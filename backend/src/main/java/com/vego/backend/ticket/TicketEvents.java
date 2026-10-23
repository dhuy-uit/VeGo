package com.vego.backend.ticket;

import com.vego.backend.booking.Booking;
import com.vego.backend.notification.Notification;
import com.vego.backend.notification.NotificationService;
import com.vego.backend.wallet.CoinLedger;
import com.vego.backend.wallet.CoinTransaction;
import com.vego.backend.wallet.UserVoucherRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Locale;

/** Việc cần làm khi một vé được thanh toán: dùng voucher Ví xu, cộng xu, gửi thông báo. */
@Component
public class TicketEvents {

    private static final Logger log = LoggerFactory.getLogger(TicketEvents.class);
    /** Mỗi 1.000đ đã trả = 1 xu. */
    public static final int VND_PER_COIN = 1000;

    private final CoinLedger ledger;
    private final NotificationService notifications;
    private final UserVoucherRepository userVouchers;
    private final Clock clock;

    public TicketEvents(CoinLedger ledger, NotificationService notifications, UserVoucherRepository userVouchers,
                        Clock clock) {
        this.ledger = ledger;
        this.notifications = notifications;
        this.userVouchers = userVouchers;
        this.clock = clock;
    }

    @Transactional
    public void onPaid(Booking b) {
        long userId = b.getUser().getId();
        if (b.getVoucherCode() != null) {
            userVouchers.findByCode(b.getVoucherCode())
                    .filter(v -> v.getUserId().equals(userId))
                    .ifPresent(v -> {
                        if (v.getBookingId() != null && !v.getBookingId().equals(b.getId())) {
                            log.warn("Voucher {} đã dùng cho đơn khác nhưng vẫn được áp vào đơn {}", v.getCode(), b.getCode());
                        }
                        v.markUsed(b.getId(), clock.instant());
                    });
        }
        int coins = b.getTotal() / VND_PER_COIN;
        if (coins > 0 && b.getCoinsEarned() == 0) {
            ledger.credit(userId, CoinTransaction.Source.BOOKING, coins, "Đặt vé " + b.getCode(), b.getCode());
            b.setCoinsEarned(coins);
        }
        notifications.push(userId, Notification.Kind.TICKET, "Vé của bạn đã được xác nhận",
                "Mã vé " + b.getCode() + " · Ghế " + b.getSeatCode() + ". Thanh toán " + methodLabel(b.getPaymentMethod())
                        + " thành công: " + money(b.getTotal()) + (coins > 0 ? ". Bạn được cộng " + coins + " xu." : "."),
                b.getCode());
    }

    public static String methodLabel(String method) {
        if (method == null) return "";
        return switch (method) {
            case "MOMO" -> "MoMo";
            case "VNPAY" -> "VNPay";
            case "CARD" -> "Thẻ ngân hàng";
            default -> method;
        };
    }

    public static String money(int amount) {
        return String.format(Locale.ROOT, "%,d", amount).replace(',', '.') + "đ";
    }
}
