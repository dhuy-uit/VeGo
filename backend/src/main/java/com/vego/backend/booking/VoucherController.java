package com.vego.backend.booking;

import com.vego.backend.home.PromotionRepository;
import com.vego.backend.security.AuthUser;
import com.vego.backend.wallet.UserVoucherRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@RestController
@Tag(name = "Đặt vé")
public class VoucherController {

    /**
     * Mã giảm giá đang áp dụng được (app hiện thành gợi ý ở màn Thanh toán).
     * source: PROMOTION (khuyến mãi chung) | WALLET (voucher đã đổi bằng xu, chỉ có khi đăng nhập).
     */
    public record VoucherInfo(String code, String title, String description, Integer discountPercent,
                              Integer discountAmount, Integer maxDiscount, int minOrder,
                              String origin, String destination, String source, Instant expiresAt) { }

    private final PromotionRepository promotions;
    private final UserVoucherRepository userVouchers;
    private final Clock clock;

    public VoucherController(PromotionRepository promotions, UserVoucherRepository userVouchers, Clock clock) {
        this.promotions = promotions;
        this.userVouchers = userVouchers;
        this.clock = clock;
    }

    @Operation(summary = "Danh sách mã giảm giá đang có (đăng nhập thì kèm voucher Ví xu của tôi)")
    @GetMapping("/api/v1/vouchers")
    public List<VoucherInfo> available(@AuthenticationPrincipal AuthUser me) {
        Instant now = clock.instant();
        List<VoucherInfo> out = new ArrayList<>();
        if (me != null) {
            userVouchers.findUsable(me.id(), now).forEach(v -> out.add(new VoucherInfo(v.getCode(), v.title(),
                    "Đơn từ " + money(v.getMinOrder()), null, v.getDiscount(), null, v.getMinOrder(),
                    null, null, "WALLET", v.getExpiresAt())));
        }
        promotions.findAvailable(now).forEach(p -> out.add(new VoucherInfo(p.getCode(), p.getTitle(),
                p.getDescription(), p.getDiscountPercent(), p.getDiscountAmount(), p.getMaxDiscount(), p.getMinOrder(),
                p.getRoute() == null ? null : p.getRoute().getOriginCity(),
                p.getRoute() == null ? null : p.getRoute().getDestinationCity(), "PROMOTION", p.getEndsAt())));
        return out;
    }

    private static String money(int amount) {
        return String.format(java.util.Locale.ROOT, "%,d", amount).replace(',', '.') + "đ";
    }
}
