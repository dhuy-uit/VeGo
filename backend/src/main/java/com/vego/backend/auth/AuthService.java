package com.vego.backend.auth;

import com.vego.backend.auth.dto.AuthResponse;
import com.vego.backend.auth.dto.LoginRequest;
import com.vego.backend.auth.dto.MessageResponse;
import com.vego.backend.auth.dto.RegisterRequest;
import com.vego.backend.auth.dto.ResetPasswordRequest;
import com.vego.backend.common.ApiException;
import com.vego.backend.common.Phones;
import com.vego.backend.common.Tokens;
import com.vego.backend.common.UrlBuilder;
import com.vego.backend.config.AppProperties;
import com.vego.backend.user.User;
import com.vego.backend.user.UserMapper;
import com.vego.backend.user.UserRepository;
import com.vego.backend.user.dto.UserResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final String FORGOT_MESSAGE =
            "Nếu tài khoản tồn tại, hướng dẫn khôi phục mật khẩu đã được gửi tới email đăng ký.";

    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordResetTokenRepository resetTokens;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final LoginAttemptService loginAttempts;
    private final MailService mailService;
    private final UserMapper userMapper;
    private final UrlBuilder urls;
    private final AppProperties props;
    private final Clock clock;

    public AuthService(UserRepository users, RefreshTokenRepository refreshTokens,
                       PasswordResetTokenRepository resetTokens, PasswordEncoder passwordEncoder,
                       TokenService tokenService, LoginAttemptService loginAttempts, MailService mailService,
                       UserMapper userMapper, UrlBuilder urls, AppProperties props, Clock clock) {
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.resetTokens = resetTokens;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.loginAttempts = loginAttempts;
        this.mailService = mailService;
        this.userMapper = userMapper;
        this.urls = urls;
        this.props = props;
        this.clock = clock;
    }

    // ---------- Đăng ký ----------

    @Transactional
    public UserResponse register(RegisterRequest req) {
        String phone = Phones.normalize(req.phone());
        String email = req.email().trim().toLowerCase(Locale.ROOT);

        Map<String, String> errors = new LinkedHashMap<>();
        if (phone == null) {
            throw ApiException.field("INVALID_PHONE", "phone", "Số điện thoại cần 10 chữ số");
        }
        if (users.existsByPhone(phone)) errors.put("phone", "Số điện thoại đã được đăng ký");
        if (users.existsByEmail(email)) errors.put("email", "Email đã được đăng ký");
        if (!errors.isEmpty()) {
            throw new ApiException(HttpStatus.CONFLICT, "ACCOUNT_EXISTS", errors.values().iterator().next(), errors);
        }

        User user = new User();
        user.setFullName(req.fullName().trim().replaceAll("\\s+", " "));
        user.setPhone(phone);
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(req.password()));
        users.save(user);
        log.info("Tài khoản mới #{} ({})", user.getId(), email);
        return userMapper.toResponse(user);
    }

    // ---------- Đăng nhập ----------

    @Transactional
    public AuthResponse login(LoginRequest req, String userAgent) {
        String key = req.account().trim().toLowerCase(Locale.ROOT);
        loginAttempts.checkAllowed(key);

        Optional<User> found = findByAccount(req.account());
        if (found.isEmpty() || !passwordEncoder.matches(req.password(), found.get().getPasswordHash())) {
            loginAttempts.recordFailure(key);
            throw ApiException.unauthorized("INVALID_CREDENTIALS", "Tài khoản hoặc mật khẩu chưa đúng");
        }
        User user = found.get();
        if (user.getStatus() == User.Status.LOCKED) {
            throw ApiException.forbidden("ACCOUNT_LOCKED", "Tài khoản đã bị khóa. Vui lòng liên hệ tổng đài hỗ trợ");
        }
        loginAttempts.reset(key);
        return tokenService.issue(user, null, req.device() != null ? req.device() : userAgent);
    }

    // ---------- Làm mới token (xoay vòng) ----------

    @Transactional(noRollbackFor = ApiException.class)
    public AuthResponse refresh(String rawToken, String userAgent) {
        Instant now = clock.instant();
        RefreshToken token = refreshTokens.findByTokenHash(Tokens.sha256(rawToken))
                .orElseThrow(() -> ApiException.unauthorized("INVALID_REFRESH_TOKEN",
                        "Phiên đăng nhập không hợp lệ, vui lòng đăng nhập lại"));

        if (token.isRevoked()) {
            // Token cũ bị dùng lại -> có thể đã lộ: thu hồi cả chuỗi phiên này
            refreshTokens.revokeFamily(token.getFamilyId(), now);
            log.warn("Phát hiện dùng lại refresh token của người dùng #{}", token.getUser().getId());
            throw ApiException.unauthorized("REFRESH_TOKEN_REUSED",
                    "Phiên đăng nhập đã kết thúc, vui lòng đăng nhập lại");
        }
        if (token.isExpired(now)) {
            throw ApiException.unauthorized("REFRESH_TOKEN_EXPIRED",
                    "Phiên đăng nhập đã hết hạn, vui lòng đăng nhập lại");
        }
        User user = token.getUser();
        if (user.getStatus() == User.Status.LOCKED) {
            refreshTokens.revokeFamily(token.getFamilyId(), now);
            throw ApiException.forbidden("ACCOUNT_LOCKED", "Tài khoản đã bị khóa. Vui lòng liên hệ tổng đài hỗ trợ");
        }
        token.revoke(now);
        String device = token.getDevice() != null ? token.getDevice() : userAgent;
        return tokenService.issue(user, token.getFamilyId(), device);
    }

    // ---------- Đăng xuất ----------

    @Transactional
    public void logout(String rawToken) {
        refreshTokens.findByTokenHash(Tokens.sha256(rawToken))
                .ifPresent(t -> refreshTokens.revokeFamily(t.getFamilyId(), clock.instant()));
    }

    // ---------- Quên / đặt lại mật khẩu ----------

    /** Luôn trả cùng một thông điệp để không lộ tài khoản nào tồn tại. */
    @Transactional
    public MessageResponse forgotPassword(String account) {
        Optional<User> found = findByAccount(account);
        if (found.isEmpty()) {
            return MessageResponse.of(FORGOT_MESSAGE);
        }
        User user = found.get();
        Instant now = clock.instant();

        // Chống spam: mỗi phút chỉ gửi 1 lần
        boolean tooSoon = resetTokens.findFirstByUserIdOrderByCreatedAtDesc(user.getId())
                .map(t -> t.getCreatedAt().isAfter(now.minus(Duration.ofSeconds(60))))
                .orElse(false);
        if (tooSoon && !props.auth().exposeResetToken()) {
            return MessageResponse.of(FORGOT_MESSAGE);
        }

        resetTokens.invalidateAllForUser(user.getId(), now);
        String raw = Tokens.randomToken();
        Duration ttl = props.auth().resetTtl();
        resetTokens.save(new PasswordResetToken(user, Tokens.sha256(raw), now.plus(ttl), now));

        String link = urls.absolute("/reset-password.html?token=" + raw);
        mailService.sendPasswordReset(user, link, ttl.toMinutes());
        return new MessageResponse(FORGOT_MESSAGE, props.auth().exposeResetToken() ? raw : null);
    }

    @Transactional
    public MessageResponse resetPassword(ResetPasswordRequest req) {
        Instant now = clock.instant();
        PasswordResetToken token = resetTokens.findByTokenHash(Tokens.sha256(req.token()))
                .filter(t -> t.isUsable(now))
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "INVALID_RESET_TOKEN",
                        "Liên kết khôi phục không hợp lệ hoặc đã hết hạn"));
        User user = token.getUser();
        user.setPasswordHash(passwordEncoder.encode(req.newPassword()));
        user.setPasswordChangedAt(now);
        token.markUsed(now);
        resetTokens.invalidateAllForUser(user.getId(), now);
        tokenService.revokeAll(user.getId());
        loginAttempts.reset(user.getEmail());
        loginAttempts.reset(user.getPhone());
        return MessageResponse.of("Đã đặt lại mật khẩu. Hãy đăng nhập bằng mật khẩu mới.");
    }

    private Optional<User> findByAccount(String account) {
        String value = account.trim();
        if (Phones.looksLikeEmail(value)) {
            return users.findByEmail(value.toLowerCase(Locale.ROOT));
        }
        String phone = Phones.normalize(value);
        return phone == null ? Optional.empty() : users.findByPhone(phone);
    }
}
