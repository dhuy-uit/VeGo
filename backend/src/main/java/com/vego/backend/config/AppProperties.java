package com.vego.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.time.ZoneId;
import java.util.List;

/** Cấu hình riêng của VéGo (khối "app:" trong application.yml). */
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        String publicBaseUrl,
        String uploadDir,
        String timezone,
        boolean seedDemoUser,
        String adminEmail,
        String adminPassword,
        Jwt jwt,
        Auth auth,
        Mail mail,
        Cors cors) {

    public record Jwt(String secret, String issuer, Duration accessTtl, Duration refreshTtl) { }

    public record Auth(Duration resetTtl, boolean exposeResetToken, int maxLoginFailures, Duration lockout) { }

    public record Mail(String from) { }

    public record Cors(List<String> allowedOrigins) { }

    public ZoneId zone() {
        return ZoneId.of(timezone == null || timezone.isBlank() ? "Asia/Ho_Chi_Minh" : timezone);
    }
}
