package com.example.vego.data;

import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;

import com.example.vego.R;
import com.example.vego.data.api.ApiModels;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Ví xu (Figma nhóm 06 · Tiện ích): số dư, lịch sử, lượt chơi game.
 * Dữ liệu lấy từ server (GET /api/v1/wallet), giữ bản gần nhất để hiện ngay khi mở màn.
 */
public final class CoinWallet {

    public enum Source {
        WELCOME("Quà chào mừng", R.drawable.ic_gift),
        CHECK_IN("Điểm danh", R.drawable.ic_calendar_check),
        GAME("Chơi game", R.drawable.ic_gamepad),
        BOOKING("Đặt vé", R.drawable.ic_ticket),
        VOUCHER("Đổi voucher", R.drawable.ic_ticket_percent),
        REFUND("Hủy vé", R.drawable.ic_ticket);

        public final String label;
        @DrawableRes public final int icon;

        Source(String label, int icon) {
            this.label = label;
            this.icon = icon;
        }

        static Source parse(@Nullable String s) {
            try {
                return s == null ? BOOKING : valueOf(s);
            } catch (IllegalArgumentException e) {
                return BOOKING;
            }
        }
    }

    /** Một dòng trong "Lịch sử giao dịch xu". */
    public static final class Transaction {
        public final Source source;
        public final String label;
        public final String time;
        public final int amount;

        Transaction(ApiModels.CoinTx tx) {
            source = Source.parse(tx.source);
            label = tx.label != null ? tx.label : source.label;
            amount = tx.amount;
            Instant at = NotificationStore.parseInstant(tx.createdAt);
            time = at == null ? "" : TIME.format(at.atZone(VN));
        }
    }

    /** Thưởng điểm danh theo ngày trong chuỗi 7 ngày (ngày 7 thưởng lớn) — giống server. */
    public static final int[] CHECK_IN_REWARDS = { 20, 20, 20, 20, 20, 20, 50 };
    /** Số lượt chơi game mỗi ngày. */
    public static final int GAME_TURNS_PER_DAY = 2;
    /** 10 điểm = 1 xu, tối đa 100 xu mỗi lượt. */
    public static final int POINTS_PER_COIN = 10;
    public static final int MAX_COINS_PER_GAME = 100;

    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy · HH:mm");

    private static final CoinWallet INSTANCE = new CoinWallet();

    public static CoinWallet get() {
        return INSTANCE;
    }

    private boolean loaded = false;
    private int balance = 0;
    private boolean checkedInToday = false;
    private int turnsLeft = GAME_TURNS_PER_DAY;
    private int bestScore = 0;
    private final List<Transaction> history = new ArrayList<>();

    private CoinWallet() { }

    public void update(@Nullable ApiModels.Wallet w) {
        if (w == null) return;
        loaded = true;
        balance = w.balance;
        checkedInToday = w.checkedInToday;
        turnsLeft = w.gameTurnsLeft;
        bestScore = w.bestScore;
        history.clear();
        if (w.history != null) for (ApiModels.CoinTx tx : w.history) history.add(new Transaction(tx));
    }

    /** Cập nhật nhanh sau một thao tác (điểm danh, chơi game, đổi voucher) trước khi tải lại cả ví. */
    public void setBalance(int value) {
        balance = value;
    }

    public void setCheckedInToday(boolean value) {
        checkedInToday = value;
    }

    public void setTurnsLeft(int value) {
        turnsLeft = value;
    }

    public void setBestScore(int value) {
        bestScore = Math.max(bestScore, value);
    }

    public void clear() {
        loaded = false;
        balance = 0;
        checkedInToday = false;
        turnsLeft = GAME_TURNS_PER_DAY;
        bestScore = 0;
        history.clear();
    }

    public boolean isLoaded() {
        return loaded;
    }

    public int balance() {
        return balance;
    }

    public boolean checkedInToday() {
        return checkedInToday;
    }

    public int turnsLeft() {
        return turnsLeft;
    }

    public int bestScore() {
        return bestScore;
    }

    public List<Transaction> history() {
        return Collections.unmodifiableList(history);
    }

    /** Định dạng xu: 1250 -> "1.250". */
    public static String coins(int value) {
        return String.format(Locale.GERMANY, "%,d", value);
    }

    public static int coinsForScore(int score) {
        return Math.min(score / POINTS_PER_COIN, MAX_COINS_PER_GAME);
    }
}
