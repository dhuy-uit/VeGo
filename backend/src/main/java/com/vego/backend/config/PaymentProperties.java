package com.vego.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Cấu hình cổng thanh toán ("app.payments").
 * simulate=true: mọi phương thức đi qua trang thanh toán mô phỏng của server (dùng khi demo / CI).
 */
@ConfigurationProperties(prefix = "app.payments")
public record PaymentProperties(boolean simulate, Vnpay vnpay, Momo momo) {

    public record Vnpay(String tmnCode, String hashSecret, String payUrl) {
        public boolean configured() {
            return tmnCode != null && !tmnCode.isBlank() && hashSecret != null && !hashSecret.isBlank();
        }
    }

    public record Momo(String partnerCode, String accessKey, String secretKey, String endpoint, String requestType) {
        public boolean configured() {
            return partnerCode != null && !partnerCode.isBlank() && secretKey != null && !secretKey.isBlank();
        }
    }
}
