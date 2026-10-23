package com.vego.backend.booking;

import com.vego.backend.home.PromotionRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.util.List;

@RestController
@Tag(name = "Đặt vé")
public class VoucherController {

    /** Mã giảm giá đang áp dụng được (app hiện thành gợi ý ở màn Thanh toán). */
    public record VoucherInfo(String code, String title, String description, Integer discountPercent,
                              Integer discountAmount, Integer maxDiscount, int minOrder,
                              String origin, String destination) { }

    private final PromotionRepository promotions;
    private final Clock clock;

    public VoucherController(PromotionRepository promotions, Clock clock) {
        this.promotions = promotions;
        this.clock = clock;
    }

    @Operation(summary = "Danh sách mã giảm giá đang có")
    @GetMapping("/api/v1/vouchers")
    public List<VoucherInfo> available() {
        return promotions.findAvailable(clock.instant()).stream()
                .map(p -> new VoucherInfo(p.getCode(), p.getTitle(), p.getDescription(), p.getDiscountPercent(),
                        p.getDiscountAmount(), p.getMaxDiscount(), p.getMinOrder(),
                        p.getRoute() == null ? null : p.getRoute().getOriginCity(),
                        p.getRoute() == null ? null : p.getRoute().getDestinationCity()))
                .toList();
    }
}
