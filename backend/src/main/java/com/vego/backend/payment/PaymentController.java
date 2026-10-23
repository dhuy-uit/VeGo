package com.vego.backend.payment;

import com.vego.backend.payment.dto.CreatePaymentRequest;
import com.vego.backend.payment.dto.PaymentResponse;
import com.vego.backend.security.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Thanh toán")
public class PaymentController {

    private final PaymentService service;

    public PaymentController(PaymentService service) {
        this.service = service;
    }

    @Operation(summary = "Tạo giao dịch thanh toán cho đơn đang giữ (MOMO / VNPAY / CARD) -> payUrl để app mở")
    @PostMapping("/api/v1/bookings/{code}/payments")
    public ResponseEntity<PaymentResponse> create(@AuthenticationPrincipal AuthUser me, @PathVariable String code,
                                                  @Valid @RequestBody CreatePaymentRequest req,
                                                  HttpServletRequest http) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.create(me.id(), code, req.method(), http.getRemoteAddr()));
    }
}
