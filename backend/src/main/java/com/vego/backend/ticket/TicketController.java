package com.vego.backend.ticket;

import com.vego.backend.security.AuthUser;
import com.vego.backend.ticket.dto.ReviewRequest;
import com.vego.backend.ticket.dto.TicketResponse;
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

import java.util.List;

@RestController
@RequestMapping("/api/v1/tickets")
@Tag(name = "Vé của tôi")
public class TicketController {

    private final TicketService service;

    public TicketController(TicketService service) {
        this.service = service;
    }

    @Operation(summary = "Vé đã thanh toán của tôi (sắp đi / đã đi / đã hủy)")
    @GetMapping
    public List<TicketResponse> list(@AuthenticationPrincipal AuthUser me) {
        return service.list(me.id());
    }

    @Operation(summary = "Vé điện tử theo mã vé")
    @GetMapping("/{code}")
    public TicketResponse get(@AuthenticationPrincipal AuthUser me, @PathVariable String code) {
        return service.get(me.id(), code);
    }

    @Operation(summary = "Hủy vé: hoàn 80% nếu trước giờ đi từ 24 giờ, sát giờ hoàn 50%")
    @PostMapping("/{code}/cancel")
    public TicketResponse cancel(@AuthenticationPrincipal AuthUser me, @PathVariable String code) {
        return service.cancel(me.id(), code);
    }

    @Operation(summary = "Đánh giá nhà xe sau chuyến đi (gửi lại để sửa)")
    @PostMapping("/{code}/review")
    public TicketResponse review(@AuthenticationPrincipal AuthUser me, @PathVariable String code,
                                 @Valid @RequestBody ReviewRequest req) {
        return service.review(me.id(), code, req);
    }
}
