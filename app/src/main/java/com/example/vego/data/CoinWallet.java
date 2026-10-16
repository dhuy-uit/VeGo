package com.example.vego.data;

import androidx.annotation.DrawableRes;

import com.example.vego.R;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Ví xu của người dùng (Figma nhóm 06 · Tiện ích): số dư, lịch sử, điểm danh 7 ngày,
 * lượt chơi game và đổi voucher. Bước prototype giữ trong bộ nhớ; sau này lấy từ REST API.
 */
public final class CoinWallet {

    public enum Source {
        CHECK_IN("Điểm danh", R.drawable.ic_calendar_check),
        GAME("Chơi game", R.drawable.ic_gamepad),
        BOOKING("Đặt vé", R.drawable.ic_ticket),
        VOUCHER("Đổi voucher", R.drawable.ic_ticket_percent);

        public final String label;
        @DrawableRes public final int icon;

        Source(String label, int icon) {
            this.label = label;
            this.icon = icon;
        }
    }

    /** Một dòng trong "Lịch sử giao dịch xu". */
    public static final class Transaction {
        public final Source source;
        public final String time;
        public final int amount;

        Transaction(Source source, String time, int amount) {
            this.source = source;
            this.time = time;
            this.amount = amount;
        }
    }

    /** Voucher có thể đổi bằng xu. */
    public static final class Voucher {
        public final String id;
        public final int discount;
        public final int minFare;
        public final int cost;
        public int owned;

        Voucher(String id, int discount, int minFare, int cost) {
            this.id = id;
            this.discount = discount;
            this.minFare = minFare;
            this.cost = cost;
        }
    }

    /** Thưởng điểm danh theo ngày trong chuỗi 7 ngày (ngày 7 thưởng lớn). */
    public static final int[] CHECK_IN_REWARDS = { 20, 20, 20, 20, 20, 20, 50 };
    /** Số lượt chơi game mỗi ngày. */
    public static final int GAME_TURNS_PER_DAY = 2;
    /** 10 điểm = 1 xu, tối đa 100 xu mỗi lượt. */
    public static final int POINTS_PER_COIN = 10;
    public static final int MAX_COINS_PER_GAME = 100;

    private static final CoinWallet INSTANCE = new CoinWallet();

    public static CoinWallet get() {
        return INSTANCE;
    }

    private int balance = 1250;
    private final List<Transaction> history = new ArrayList<>();
    private final List<Voucher> vouchers = new ArrayList<>();
    /** Ngày đầu của tuần điểm danh hiện tại (15/10/2026 như Figma). */
    private final Calendar weekStart = Calendar.getInstance();
    /** Số ngày đã điểm danh liên tiếp trong tuần (Figma: 3 ngày trước hôm nay). */
    private int streak = 3;
    private boolean checkedInToday = false;
    private int turnsLeft = GAME_TURNS_PER_DAY;
    private int bestScore = 0;

    private CoinWallet() {
        weekStart.clear();
        weekStart.set(2026, Calendar.OCTOBER, 15);

        history.add(new Transaction(Source.CHECK_IN, "17/10/2026 · 08:15", 20));
        history.add(new Transaction(Source.GAME, "16/10/2026 · 20:30", 80));
        history.add(new Transaction(Source.BOOKING, "16/10/2026 · 14:05", 320));
        history.add(new Transaction(Source.VOUCHER, "16/10/2026 · 09:10", -500));
        history.add(new Transaction(Source.CHECK_IN, "16/10/2026 · 08:20", 20));

        vouchers.add(new Voucher("V20", 20_000, 200_000, 500));
        vouchers.add(new Voucher("V50", 50_000, 300_000, 1000));
        vouchers.add(new Voucher("V100", 100_000, 500_000, 2000));
    }

    // ---------- Số dư & lịch sử ----------

    public int balance() {
        return balance;
    }

    public List<Transaction> history() {
        return Collections.unmodifiableList(history);
    }

    private void record(Source source, int amount) {
        balance += amount;
        history.add(0, new Transaction(source, nowText(), amount));
    }

    /** "Hôm nay" của bản demo là 18/10/2026 (khớp Figma), giờ lấy theo đồng hồ máy. */
    public Calendar today() {
        return Format.plusDays(weekStart, streakDayIndexToday());
    }

    private int streakDayIndexToday() {
        return 3; // 15/10 + 3 ngày = 18/10
    }

    private String nowText() {
        Calendar now = Calendar.getInstance();
        return String.format(Locale.ROOT, "%s · %02d:%02d", Format.date(today()),
                now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE));
    }

    /** Định dạng xu: 1250 -> "1.250". */
    public static String coins(int value) {
        return String.format(Locale.GERMANY, "%,d", value);
    }

    // ---------- Điểm danh ----------

    public Calendar weekDay(int index) {
        return Format.plusDays(weekStart, index);
    }

    public int todayIndex() {
        return streakDayIndexToday();
    }

    public int streak() {
        return streak;
    }

    public boolean checkedInToday() {
        return checkedInToday;
    }

    public int todayReward() {
        return CHECK_IN_REWARDS[todayIndex()];
    }

    /** Điểm danh hôm nay, trả về số xu nhận được (0 nếu đã điểm danh rồi). */
    public int checkIn() {
        if (checkedInToday) return 0;
        int reward = todayReward();
        checkedInToday = true;
        streak++;
        record(Source.CHECK_IN, reward);
        return reward;
    }

    // ---------- Game ----------

    public int turnsLeft() {
        return turnsLeft;
    }

    public int bestScore() {
        return bestScore;
    }

    public static int coinsForScore(int score) {
        return Math.min(score / POINTS_PER_COIN, MAX_COINS_PER_GAME);
    }

    /** Bắt đầu một lượt: trừ lượt chơi. Trả về false nếu đã hết lượt. */
    public boolean startGame() {
        if (turnsLeft <= 0) return false;
        turnsLeft--;
        return true;
    }

    /** Kết thúc lượt: đổi điểm ra xu và cộng vào ví. */
    public int finishGame(int score) {
        int earned = coinsForScore(score);
        if (score > bestScore) bestScore = score;
        if (earned > 0) record(Source.GAME, earned);
        return earned;
    }

    // ---------- Đặt vé ----------

    /** Mỗi 1.000đ tiền vé = 1 xu. */
    public int rewardBooking(int fare) {
        int earned = fare / 1000;
        if (earned > 0) record(Source.BOOKING, earned);
        return earned;
    }

    // ---------- Voucher ----------

    public List<Voucher> vouchers() {
        return Collections.unmodifiableList(vouchers);
    }

    public boolean canRedeem(Voucher v) {
        return balance >= v.cost;
    }

    /** Đổi xu lấy voucher, trả về mã voucher (null nếu không đủ xu). */
    public String redeem(Voucher v) {
        if (!canRedeem(v)) return null;
        record(Source.VOUCHER, -v.cost);
        v.owned++;
        return String.format(Locale.ROOT, "VEGO%dK-%04d", v.discount / 1000,
                (int) (System.currentTimeMillis() % 10000));
    }
}
