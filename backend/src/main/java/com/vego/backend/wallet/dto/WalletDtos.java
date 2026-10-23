package com.vego.backend.wallet.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Dữ liệu Ví xu gửi / nhận. */
public final class WalletDtos {

    private WalletDtos() { }

    /** source: WELCOME | CHECK_IN | GAME | BOOKING | VOUCHER | REFUND; label: tên hiển thị. */
    public record Transaction(long id, String source, String label, int amount, String note, String ref,
                              Instant createdAt) { }

    public record Wallet(int balance, boolean checkedInToday, int gameTurnsLeft, int gameTurnsPerDay,
                         int bestScore, int availableVouchers, List<Transaction> history) { }

    /** state: CHECKED (đã điểm danh) | TODAY (hôm nay, chưa điểm danh) | LOCKED (ngày tới). */
    public record CheckInDay(int index, LocalDate date, int reward, String state) { }

    /**
     * Chu kỳ điểm danh 7 ngày: todayIndex = vị trí hôm nay (0..6), streak = số ngày liên tiếp,
     * daysToBonus = còn bao nhiêu ngày tới ngày thưởng lớn (ngày 7).
     */
    public record CheckInStatus(LocalDate today, boolean checkedInToday, int streak, int todayIndex, int todayReward,
                                int daysToBonus, int balance, List<CheckInDay> days) { }

    public record CheckInResult(int reward, CheckInStatus status) { }

    public record GameStart(long playId, int turnsLeft, int turnsPerDay, int bestScore) { }

    public record GameFinishRequest(
            @NotNull(message = "Thiếu điểm")
            @Min(value = 0, message = "Điểm không hợp lệ")
            @Max(value = 1_000_000, message = "Điểm không hợp lệ")
            Integer score) { }

    public record GameResult(int score, int coins, boolean newBest, int bestScore, int balance, int turnsLeft,
                             int maxCoinsPerGame) { }

    public record Offer(String id, int discount, int minOrder, int cost, int validDays, int owned,
                        boolean affordable) { }

    /** status: AVAILABLE | USED | EXPIRED. */
    public record MyVoucher(String code, String title, int discount, int minOrder, String status,
                            Instant expiresAt, Instant usedAt) { }

    public record Vouchers(int balance, List<Offer> offers, List<MyVoucher> mine) { }

    public record Redeemed(MyVoucher voucher, int balance) { }
}
