package com.vego.backend.booking;

import com.vego.backend.common.ApiException;
import com.vego.backend.home.Promotion;
import com.vego.backend.home.PromotionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.Locale;

/**
 * Kiểm tra mã giảm giá và tính số tiền giảm cho một đơn.
 * Dùng chung bảng promotions với banner Trang chủ (mã có show_on_home=false chỉ dùng khi thanh toán).
 */
@Service
public class VoucherService {

    /** Tổng tiền sau giảm không thấp hơn mức này (cổng thanh toán có số tiền tối thiểu). */
    static final int MIN_TOTAL = 10_000;

    private final PromotionRepository promotions;
    private final BookingRepository bookings;
    private final Clock clock;

    public VoucherService(PromotionRepository promotions, BookingRepository bookings, Clock clock) {
        this.promotions = promotions;
        this.bookings = bookings;
        this.clock = clock;
    }

    public record Applied(Promotion promotion, int discount) { }

    public Applied evaluate(Booking booking, String rawCode) {
        String code = rawCode.trim().toUpperCase(Locale.ROOT);
        Promotion p = promotions.findByCode(code)
                .filter(Promotion::isActive)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "VOUCHER_NOT_FOUND",
                        "Mã giảm giá không tồn tại"));
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
        discount = (discount / 1000) * 1000;   // làm tròn xuống nghìn đồng
        discount = Math.max(0, Math.min(discount, booking.getFare() - MIN_TOTAL));
        return new Applied(p, discount);
    }

    private static ApiException voucherError(String code, String message) {
        return ApiException.field(code, "code", message);
    }

    private static String money(int amount) {
        return String.format(Locale.ROOT, "%,d", amount).replace(',', '.') + "đ";
    }
}
