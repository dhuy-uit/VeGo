package com.vego.backend.wallet;

import com.vego.backend.security.AuthUser;
import com.vego.backend.wallet.dto.WalletDtos;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/wallet")
@Tag(name = "Ví xu")
public class WalletController {

    private final WalletService service;

    public WalletController(WalletService service) {
        this.service = service;
    }

    @Operation(summary = "Số dư, lượt chơi còn lại và lịch sử giao dịch xu")
    @GetMapping
    public WalletDtos.Wallet wallet(@AuthenticationPrincipal AuthUser me) {
        return service.wallet(me.id());
    }

    @Operation(summary = "Trạng thái điểm danh 7 ngày")
    @GetMapping("/check-in")
    public WalletDtos.CheckInStatus checkInStatus(@AuthenticationPrincipal AuthUser me) {
        return service.checkInStatus(me.id());
    }

    @Operation(summary = "Điểm danh hôm nay (nhận xu)")
    @PostMapping("/check-in")
    public WalletDtos.CheckInResult checkIn(@AuthenticationPrincipal AuthUser me) {
        return service.checkIn(me.id());
    }

    @Operation(summary = "Bắt đầu một lượt chơi game (trừ 1 lượt, mỗi ngày 2 lượt)")
    @PostMapping("/game/start")
    public WalletDtos.GameStart startGame(@AuthenticationPrincipal AuthUser me) {
        return service.startGame(me.id());
    }

    @Operation(summary = "Kết thúc lượt chơi: gửi điểm, nhận xu (10 điểm = 1 xu, tối đa 100 xu)")
    @PostMapping("/game/{playId}/finish")
    public WalletDtos.GameResult finishGame(@AuthenticationPrincipal AuthUser me, @PathVariable long playId,
                                            @Valid @RequestBody WalletDtos.GameFinishRequest req) {
        return service.finishGame(me.id(), playId, req.score());
    }

    @Operation(summary = "Voucher đổi được bằng xu và voucher của tôi")
    @GetMapping("/vouchers")
    public WalletDtos.Vouchers vouchers(@AuthenticationPrincipal AuthUser me) {
        return service.vouchers(me.id());
    }

    @Operation(summary = "Đổi xu lấy voucher (nhận mã dùng ở màn Thanh toán)")
    @PostMapping("/vouchers/{offerId}/redeem")
    public WalletDtos.Redeemed redeem(@AuthenticationPrincipal AuthUser me, @PathVariable String offerId) {
        return service.redeem(me.id(), offerId);
    }
}
