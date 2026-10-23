package com.vego.backend.wallet;

import com.vego.backend.common.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Locale;

/** Sổ xu: mọi thay đổi số dư đi qua đây để luôn có dòng lịch sử tương ứng. */
@Component
public class CoinLedger {

    private final CoinTransactionRepository txs;
    private final Clock clock;

    public CoinLedger(CoinTransactionRepository txs, Clock clock) {
        this.txs = txs;
        this.clock = clock;
    }

    /** Cộng xu, trả về số dư mới. */
    @Transactional
    public int credit(long userId, CoinTransaction.Source source, int amount, String note, String ref) {
        if (amount <= 0) return balance(userId);
        txs.adjustBalance(userId, amount);
        txs.save(new CoinTransaction(userId, source, amount, note, ref, clock.instant()));
        return balance(userId);
    }

    /** Trừ xu; không đủ thì báo lỗi NOT_ENOUGH_COINS. */
    @Transactional
    public int debit(long userId, CoinTransaction.Source source, int amount, String note, String ref) {
        if (txs.adjustBalance(userId, -amount) == 0) {
            int missing = amount - balance(userId);
            throw new ApiException(HttpStatus.CONFLICT, "NOT_ENOUGH_COINS",
                    "Bạn còn thiếu " + coins(missing) + " xu");
        }
        txs.save(new CoinTransaction(userId, source, -amount, note, ref, clock.instant()));
        return balance(userId);
    }

    /** Thu hồi tối đa amount xu (vd hủy vé đã được cộng xu); số dư không xuống dưới 0. Trả về số xu đã trừ. */
    @Transactional
    public int debitUpTo(long userId, CoinTransaction.Source source, int amount, String note, String ref) {
        int take = Math.min(amount, balance(userId));
        if (take <= 0) return 0;
        if (txs.adjustBalance(userId, -take) == 0) return 0;
        txs.save(new CoinTransaction(userId, source, -take, note, ref, clock.instant()));
        return take;
    }

    public int balance(long userId) {
        Integer b = txs.balanceOf(userId);
        return b == null ? 0 : b;
    }

    public static String coins(int value) {
        return String.format(Locale.ROOT, "%,d", value).replace(',', '.');
    }
}
