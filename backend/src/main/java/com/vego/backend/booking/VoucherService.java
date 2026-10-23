package com.vego.backend.booking;

import com.vego.backend.common.ApiException;
import com.vego.backend.home.Promotion;
import com.vego.backend.home.PromotionRepository;
import com.vego.backend.wallet.UserVoucher;
import com.vego.backend.wallet.UserVoucherRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.Locale;

/**
 * Kiểm tra mã giảm giá và tính số tiền giảm cho một đơn. Hai loại mã:
 * <ul>
 *   <li>Khuyến mãi chung (bảng promotions, dùng chung với banner Trang chủ; show_on_home=false chỉ dùng khi thanh toán).</li>
 *   <li>Voucher đổi bằng xu (bảng user_vouchers): chỉ chủ voucher dùng được, mỗi mã 1 lần.</li>
 * </ul>
 */
@Service
public class VoucherService {

    /** Tổng tiền sau giảm không thấp hơn mức này (cổng thanh toán có số tiền tối thiểu). */
    static final int MIN_TOTAL = 10_000;

    private final PromotionRepository promotions;
    private final BookingRepository bookings;
    private final UserVoucherRepository userVouchers;
    private final Clock clock;

    public VoucherService(PromotionRepository promotions, BookingRepository bookings,
                          UserVoucherRepository userVouchers, Clock clock) {
        this.promotions = promotions;
        this.bookings = bookings;
        this.userVouchers = userVouchers;
        this.clock = clock;
    }

    /** code: mã đã chuẩn hóa (viết hoa), title: tên hiển thị. */
    public record Applied(String code, String title, int discount) { }

    public Applied evaluate(Booking booking, String rawCode) {
        String code = rawCode.trim().toUpperCase(Locale.ROOT);
        Promotion p = promotions.findByCode(code).filter(Promotion::isActive).orElse(null);
        if (p == null) {
            UserVoucher v = userVouchers.findByCode(code)
                    .filter(x -> x.getUserId().equals(booking.getUser().getId()))
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "VOUCHER_NOT_FOUND",
                            "Mã giảm giá không tồn tại"));
            return evaluateWalletVoucher(booking, v);
        }
        Instant now = clock.instant();
        if (p.getStartsAt().isAfter(now)) {
            throw voucherError("VOUCHER_NOT_STARTED", "Mã giảm giá chưa đến thời gian áp dụng");
        }
        if (p.getEndsAt().isBefore(now)) {
            throw voucherError("VOUCHER_EXPIRED", "Mã giảm giá đã hết hạn");
        }
        if (p.getRoute() != null && !p.getRoute().getId().equals(booking.getTrip().getRoute().getId())) {
            throw voucherError("VOUCHER_NOT_APPLICABLE", "Mã chỉ áp dụng cho tuyến "
                    + p.getRoute().getOriginCity() + " – " + p.getRoute().getDestinationCity());
        }
        if (booking.getFare() < p.getMinOrder()) {
            throw voucherError("VOUCHER_MIN_ORDER", "Mã áp dụng cho đơn từ " + money(p.getMinOrder()));
        }
        long used = bookings.countPaidWithVoucher(booking.getUser().getId(), p.getCode());
        if (used >= p.getPerUserLimit()) {
            throw voucherError("VOUCHER_USED", p.getPerUserLimit() == 1
                    ? "Bạn đã dùng mã này rồi"
                    : "Bạn đã dùng hết " + p.getPerUserLimit() + " lượt của mã này");
        }

        int discount;
        if (p.getDiscountPercent() != null) {
            discount = booking.getFare() * p.getDiscountPercent() / 100;
            if (p.getMaxDiscount() != null) discount = Math.min(discount, p.getMaxDiscount());
        } else {
            discount = p.getDiscountAmount();
        }
        return new Applied(p.getCode(), p.getTitle(), clamp(booking, discount));
    }

    private Applied evaluateWalletVoucher(Booking booking, UserVoucher v) {
        if (v.getStatus() == UserVoucher.Status.USED) {
            throw voucherError("VOUCHER_USED", "Voucher này đã được dùng");
        }
        if (!v.getExpiresAt().isAfter(clock.instant())) {
            throw voucherError("VOUCHER_EXPIRED", "Voucher đã hết hạn");
        }
        if (booking.getFare() < v.getMinOrder()) {
            throw voucherError("VOUCHER_MIN_ORDER", "Voucher áp dụng cho đơn từ " + money(v.getMinOrder()));
        }
        return new Applied(v.getCode(), v.title(), clamp(booking, v.getDiscount()));
    }

    private static int clamp(Booking booking, int discount) {
        discount = (discount / 1000) * 1000;   // làm tròn xuống nghìn đồng
        return Math.max(0, Math.min(discount, booking.getFare() - MIN_TOTAL));
    }

    /** Tên hiển thị của mã đã áp vào đơn (khuyến mãi hoặc voucher Ví xu). */
    public String titleOf(String code) {
        if (code == null) return null;
        return promotions.findByCode(code).map(Promotion::getTitle)
                .or(() -> userVouchers.findByCode(code).map(UserVoucher::title))
                .orElse(null);
    }

    private static ApiException voucherError(String code, String message) {
        return ApiException.field(code, "code", message);
    }

    private static String money(int amount) {
        return String.format(Locale.ROOT, "%,d", amount).replace(',', '.') + "đ";
    }
}
