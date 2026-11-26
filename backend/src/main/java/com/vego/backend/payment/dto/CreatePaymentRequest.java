package com.vego.backend.payment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** method: VNPAY | CARD (thẻ ngân hàng, đi qua cổng VNPay). */
public record CreatePaymentRequest(
        @NotBlank(message = "Vui lòng chọn phương thức thanh toán")
        @Pattern(regexp = "VNPAY|CARD", message = "Phương thức thanh toán không hỗ trợ")
        String method) {
}
