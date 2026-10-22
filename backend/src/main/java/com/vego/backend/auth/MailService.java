package com.vego.backend.auth;

import com.vego.backend.config.AppProperties;
import com.vego.backend.user.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Gửi email khôi phục mật khẩu. Nếu chưa cấu hình SMTP (SPRING_MAIL_HOST),
 * chỉ ghi link ra log để tiện thử nghiệm.
 */
@Service
public class MailService {

    private static final Logger log = LoggerFactory.getLogger(MailService.class);

    private final ObjectProvider<JavaMailSender> mailSender;
    private final AppProperties props;
    private final boolean configured;

    public MailService(ObjectProvider<JavaMailSender> mailSender, AppProperties props,
                       @Value("${spring.mail.host:}") String mailHost) {
        this.mailSender = mailSender;
        this.props = props;
        this.configured = mailHost != null && !mailHost.isBlank();
    }

    @Async
    public void sendPasswordReset(User user, String link, long minutes) {
        JavaMailSender sender = configured ? mailSender.getIfAvailable() : null;
        if (sender == null) {
            log.info("[Chưa cấu hình email] Link khôi phục mật khẩu cho {}: {}", user.getEmail(), link);
            return;
        }
        SimpleMailMessage msg = new SimpleMailMessage();
        msg.setFrom(props.mail().from());
        msg.setTo(user.getEmail());
        msg.setSubject("VéGo - Khôi phục mật khẩu");
        msg.setText("""
                Chào %s,

                Bạn (hoặc ai đó) vừa yêu cầu khôi phục mật khẩu tài khoản VéGo.
                Mở liên kết sau để đặt mật khẩu mới (hiệu lực trong %d phút):

                %s

                Nếu không phải bạn yêu cầu, hãy bỏ qua email này.

                VéGo - Đặt vé dễ dàng, an tâm mọi hành trình.
                """.formatted(user.givenName(), minutes, link));
        try {
            sender.send(msg);
        } catch (Exception e) {
            log.error("Không gửi được email khôi phục tới {}: {}", user.getEmail(), e.getMessage());
            log.info("Link khôi phục mật khẩu cho {}: {}", user.getEmail(), link);
        }
    }
}
