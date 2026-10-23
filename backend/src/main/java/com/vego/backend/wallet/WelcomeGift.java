package com.vego.backend.wallet;

import com.vego.backend.notification.Notification;
import com.vego.backend.notification.NotificationService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Quà cho tài khoản mới: 500 xu chào mừng + thông báo giới thiệu mã CHAOBANMOI. */
@Component
public class WelcomeGift {

    public static final int WELCOME_COINS = 500;

    private final CoinLedger ledger;
    private final NotificationService notifications;

    public WelcomeGift(CoinLedger ledger, NotificationService notifications) {
        this.ledger = ledger;
        this.notifications = notifications;
    }

    @Transactional
    public void give(long userId) {
        ledger.credit(userId, CoinTransaction.Source.WELCOME, WELCOME_COINS, "Quà chào mừng thành viên mới", null);
        notifications.push(userId, Notification.Kind.PROMO, "Chào mừng bạn đến với VéGo!",
                "Tặng bạn " + WELCOME_COINS + " xu trong Ví xu (đổi được voucher 20.000đ) và mã CHAOBANMOI giảm "
                        + "50.000đ cho chuyến đầu tiên từ 200.000đ.",
                null);
    }
}
