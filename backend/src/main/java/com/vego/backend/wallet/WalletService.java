package com.vego.backend.wallet;

import com.vego.backend.common.ApiException;
import com.vego.backend.config.AppProperties;
import com.vego.backend.wallet.dto.WalletDtos;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/** Ví xu: số dư, lịch sử, điểm danh 7 ngày, game kiếm xu, đổi voucher. */
@Service
public class WalletService {

    /** Thưởng điểm danh theo ngày trong chu kỳ 7 ngày (ngày 7 thưởng lớn). */
    public static final int[] CHECK_IN_REWARDS = {20, 20, 20, 20, 20, 20, 50};
    public static final int GAME_TURNS_PER_DAY = 2;
    /** 10 điểm = 1 xu, tối đa 100 xu mỗi lượt. */
    public static final int POINTS_PER_COIN = 10;
    public static final int MAX_COINS_PER_GAME = 100;
    /** Giới hạn điểm hợp lý: tối đa 100 điểm mỗi giây chơi (chống gửi điểm giả). */
    private static final int MAX_POINTS_PER_SECOND = 100;
    private static final int HISTORY_LIMIT = 50;
    private static final String CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private static final Map<CoinTransaction.Source, String> LABELS = Map.of(
            CoinTransaction.Source.WELCOME, "Quà chào mừng",
            CoinTransaction.Source.CHECK_IN, "Điểm danh",
            CoinTransaction.Source.GAME, "Chơi game",
            CoinTransaction.Source.BOOKING, "Đặt vé",
            CoinTransaction.Source.VOUCHER, "Đổi voucher",
            CoinTransaction.Source.REFUND, "Hủy vé");

    private final CoinLedger ledger;
    private final CoinTransactionRepository txs;
    private final CheckInRepository checkIns;
    private final GamePlayRepository games;
    private final VoucherOfferRepository offers;
    private final UserVoucherRepository userVouchers;
    private final ZoneId zone;
    private final Clock clock;

    public WalletService(CoinLedger ledger, CoinTransactionRepository txs, CheckInRepository checkIns,
                         GamePlayRepository games, VoucherOfferRepository offers, UserVoucherRepository userVouchers,
                         AppProperties props, Clock clock) {
        this.ledger = ledger;
        this.txs = txs;
        this.checkIns = checkIns;
        this.games = games;
        this.offers = offers;
        this.userVouchers = userVouchers;
        this.zone = props.zone();
        this.clock = clock;
    }

    private LocalDate today() {
        return LocalDate.now(clock.withZone(zone));
    }

    // ---------- Ví ----------

    @Transactional(readOnly = true)
    public WalletDtos.Wallet wallet(long userId) {
        List<WalletDtos.Transaction> history = txs
                .findByUserIdOrderByCreatedAtDescIdDesc(userId, PageRequest.of(0, HISTORY_LIMIT)).stream()
                .map(WalletService::toTx).toList();
        int usable = userVouchers.findUsable(userId, clock.instant()).size();
        return new WalletDtos.Wallet(ledger.balance(userId), checkInState(userId).checkedToday(),
                turnsLeft(userId), GAME_TURNS_PER_DAY, games.bestScore(userId), usable, history);
    }

    static WalletDtos.Transaction toTx(CoinTransaction t) {
        return new WalletDtos.Transaction(t.getId(), t.getSource().name(), LABELS.get(t.getSource()), t.getAmount(),
                t.getNote(), t.getRef(), t.getCreatedAt());
    }

    // ---------- Điểm danh ----------

    /** Trạng thái điểm danh hôm nay suy ra từ lần điểm danh gần nhất. */
    private record CheckInState(boolean checkedToday, int streak, int cycleDay) { }

    private CheckInState checkInState(long userId) {
        LocalDate today = today();
        Optional<CheckIn> last = checkIns.findTopByUserIdOrderByDayDesc(userId);
        if (last.isPresent() && last.get().getDay().equals(today)) {
            return new CheckInState(true, last.get().getStreak(), last.get().getCycleDay());
        }
        if (last.isPresent() && last.get().getDay().equals(today.minusDays(1))) {
            return new CheckInState(false, last.get().getStreak(), last.get().getCycleDay() % 7 + 1);
        }
        return new CheckInState(false, 0, 1);   // chưa điểm danh hoặc đã đứt chuỗi
    }

    @Transactional(readOnly = true)
    public WalletDtos.CheckInStatus checkInStatus(long userId) {
        return toStatus(userId, checkInState(userId));
    }

    private WalletDtos.CheckInStatus toStatus(long userId, CheckInState s) {
        LocalDate today = today();
        int todayIndex = s.cycleDay() - 1;
        LocalDate start = today.minusDays(todayIndex);
        List<WalletDtos.CheckInDay> days = new ArrayList<>();
        for (int i = 0; i < CHECK_IN_REWARDS.length; i++) {
            String state = i < todayIndex || (i == todayIndex && s.checkedToday()) ? "CHECKED"
                    : i == todayIndex ? "TODAY" : "LOCKED";
            days.add(new WalletDtos.CheckInDay(i, start.plusDays(i), CHECK_IN_REWARDS[i], state));
        }
        // Số ngày sau hôm nay mới tới ngày thưởng lớn (ngày 7); 0 = hôm nay là ngày 7
        int daysToBonus = CHECK_IN_REWARDS.length - s.cycleDay();
        return new WalletDtos.CheckInStatus(today, s.checkedToday(), s.streak(), todayIndex,
                CHECK_IN_REWARDS[todayIndex], daysToBonus, ledger.balance(userId), days);
    }

    @Transactional
    public WalletDtos.CheckInResult checkIn(long userId) {
        CheckInState s = checkInState(userId);
        if (s.checkedToday()) {
            throw new ApiException(HttpStatus.CONFLICT, "ALREADY_CHECKED_IN", "Hôm nay bạn đã điểm danh rồi");
        }
        int reward = CHECK_IN_REWARDS[s.cycleDay() - 1];
        try {
            checkIns.saveAndFlush(new CheckIn(userId, today(), s.streak() + 1, s.cycleDay(), reward, clock.instant()));
        } catch (DataIntegrityViolationException e) {
            // Bấm 2 lần cùng lúc: chỉ lần đầu được tính
            throw new ApiException(HttpStatus.CONFLICT, "ALREADY_CHECKED_IN", "Hôm nay bạn đã điểm danh rồi");
        }
        ledger.credit(userId, CoinTransaction.Source.CHECK_IN, reward,
                "Điểm danh ngày " + s.cycleDay() + "/7", null);
        return new WalletDtos.CheckInResult(reward,
                toStatus(userId, new CheckInState(true, s.streak() + 1, s.cycleDay())));
    }

    // ---------- Game ----------

    private int turnsLeft(long userId) {
        return (int) Math.max(0, GAME_TURNS_PER_DAY - games.countByUserIdAndDay(userId, today()));
    }

    @Transactional
    public WalletDtos.GameStart startGame(long userId) {
        if (turnsLeft(userId) <= 0) {
            throw new ApiException(HttpStatus.CONFLICT, "NO_TURNS_LEFT",
                    "Bạn đã dùng hết " + GAME_TURNS_PER_DAY + " lượt chơi hôm nay. Quay lại vào ngày mai nhé!");
        }
        GamePlay play = games.save(new GamePlay(userId, today(), clock.instant()));
        return new WalletDtos.GameStart(play.getId(), turnsLeft(userId), GAME_TURNS_PER_DAY, games.bestScore(userId));
    }

    @Transactional
    public WalletDtos.GameResult finishGame(long userId, long playId, int rawScore) {
        GamePlay play = games.lockOwned(playId, userId)
                .orElseThrow(() -> ApiException.notFound("GAME_NOT_FOUND", "Không tìm thấy lượt chơi"));
        if (play.getFinishedAt() != null) {
            throw new ApiException(HttpStatus.CONFLICT, "GAME_FINISHED", "Lượt chơi này đã kết thúc");
        }
        int bestBefore = games.bestScore(userId);
        Instant now = clock.instant();
        long seconds = Math.max(1, Duration.between(play.getStartedAt(), now).toSeconds());
        int score = (int) Math.min(rawScore, seconds * MAX_POINTS_PER_SECOND + 100);
        int coins = coinsForScore(score);
        play.finish(now, score, coins);
        games.saveAndFlush(play);
        int balance = coins > 0
                ? ledger.credit(userId, CoinTransaction.Source.GAME, coins, "Khủng long phiêu lưu · " + score + " điểm", null)
                : ledger.balance(userId);
        return new WalletDtos.GameResult(score, coins, score > bestBefore && score > 0, Math.max(bestBefore, score),
                balance, turnsLeft(userId), MAX_COINS_PER_GAME);
    }

    public static int coinsForScore(int score) {
        return Math.min(score / POINTS_PER_COIN, MAX_COINS_PER_GAME);
    }

    // ---------- Voucher đổi bằng xu ----------

    @Transactional(readOnly = true)
    public WalletDtos.Vouchers vouchers(long userId) {
        int balance = ledger.balance(userId);
        Instant now = clock.instant();
        List<UserVoucher> mine = userVouchers.findByUserIdOrderByCreatedAtDesc(userId);
        Map<String, Long> owned = mine.stream().filter(v -> v.usableAt(now))
                .collect(Collectors.groupingBy(UserVoucher::getOfferId, Collectors.counting()));
        List<WalletDtos.Offer> list = offers.findByActiveTrueOrderBySortOrderAsc().stream()
                .map(o -> new WalletDtos.Offer(o.getId(), o.getDiscount(), o.getMinOrder(), o.getCost(),
                        o.getValidDays(), owned.getOrDefault(o.getId(), 0L).intValue(), balance >= o.getCost()))
                .toList();
        // Còn dùng được lên trước
        List<WalletDtos.MyVoucher> myList = mine.stream()
                .sorted((a, b) -> Boolean.compare(b.usableAt(now), a.usableAt(now)))
                .map(v -> toMine(v, now)).toList();
        return new WalletDtos.Vouchers(balance, list, myList);
    }

    @Transactional
    public WalletDtos.Redeemed redeem(long userId, String offerId) {
        VoucherOffer offer = offers.findById(offerId.trim().toUpperCase(Locale.ROOT))
                .filter(VoucherOffer::isActive)
                .orElseThrow(() -> ApiException.notFound("OFFER_NOT_FOUND", "Không tìm thấy voucher"));
        String code = newCode(offer);
        int balance = ledger.debit(userId, CoinTransaction.Source.VOUCHER, offer.getCost(),
                "Voucher giảm " + CoinLedger.coins(offer.getDiscount()) + "đ", code);
        Instant now = clock.instant();
        UserVoucher v = userVouchers.save(new UserVoucher(code, userId, offer, now,
                now.plus(Duration.ofDays(offer.getValidDays()))));
        return new WalletDtos.Redeemed(toMine(v, now), balance);
    }

    static WalletDtos.MyVoucher toMine(UserVoucher v, Instant now) {
        String status = v.getStatus() == UserVoucher.Status.USED ? "USED"
                : v.getExpiresAt().isAfter(now) ? "AVAILABLE" : "EXPIRED";
        return new WalletDtos.MyVoucher(v.getCode(), v.title(), v.getDiscount(), v.getMinOrder(), status,
                v.getExpiresAt(), v.getUsedAt());
    }

    private String newCode(VoucherOffer offer) {
        for (int attempt = 0; attempt < 10; attempt++) {
            StringBuilder sb = new StringBuilder("XU").append(offer.getDiscount() / 1000).append("K-");
            for (int i = 0; i < 4; i++) sb.append(CODE_CHARS.charAt(RANDOM.nextInt(CODE_CHARS.length())));
            if (!userVouchers.existsByCode(sb.toString())) return sb.toString();
        }
        throw new IllegalStateException("Không sinh được mã voucher");
    }
}
