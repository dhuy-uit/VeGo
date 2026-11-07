package com.vego.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

import java.util.TimeZone;

/** REST API cho ứng dụng đặt vé xe khách VéGo. */
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@ConfigurationPropertiesScan
public class VeGoApplication {

    static {
        // JVM luôn chạy giờ UTC (giống CI). Giờ Việt Nam được xử lý tường minh qua app.timezone.
        // Nếu JVM chạy Asia/Ho_Chi_Minh, cột TIME (giờ khởi hành) bị lệch +8 giờ khi đọc qua JDBC
        // vì năm 1970 Sài Gòn dùng UTC+8 (lỗi đã gặp trên server Azure: chuyến 22:00 thành 06:00).
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    }

    public static void main(String[] args) {
        SpringApplication.run(VeGoApplication.class, args);
    }
}
