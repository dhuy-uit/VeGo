package com.vego.backend.seed;

import com.vego.backend.config.AppProperties;
import com.vego.backend.user.User;
import com.vego.backend.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Locale;

/**
 * Tạo tài khoản mẫu khi CSDL còn trống (giống người dùng mẫu trong app/Figma)
 * và tài khoản quản trị nếu có đặt ADMIN_EMAIL + ADMIN_PASSWORD.
 */
@Component
@Order(1)
public class DemoDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final AppProperties props;

    public DemoDataSeeder(UserRepository users, PasswordEncoder encoder, AppProperties props) {
        this.users = users;
        this.encoder = encoder;
        this.props = props;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (props.seedDemoUser() && !users.existsByPhone("0901234567")
                && !users.existsByEmail("minhanh@gmail.com")) {
            User u = new User();
            u.setFullName("Nguyễn Minh Anh");
            u.setPhone("0901234567");
            u.setEmail("minhanh@gmail.com");
            u.setBirthDate(LocalDate.of(1998, 5, 12));
            u.setPasswordHash(encoder.encode("vego123456"));
            users.save(u);
            log.info("Đã tạo tài khoản mẫu 0901234567 / vego123456");
        }

        String adminEmail = props.adminEmail();
        String adminPassword = props.adminPassword();
        if (adminEmail != null && !adminEmail.isBlank() && adminPassword != null && !adminPassword.isBlank()
                && !users.existsByRole(User.Role.ADMIN)) {
            String email = adminEmail.trim().toLowerCase(Locale.ROOT);
            if (users.existsByEmail(email)) {
                log.warn("Không tạo admin: email {} đã được dùng", email);
                return;
            }
            User admin = new User();
            admin.setFullName("Quản trị VéGo");
            admin.setPhone("0000000000");
            admin.setEmail(email);
            admin.setRole(User.Role.ADMIN);
            admin.setPasswordHash(encoder.encode(adminPassword));
            users.save(admin);
            log.info("Đã tạo tài khoản quản trị {}", email);
        }
    }
}
