package com.vego.backend.auth;

import com.vego.backend.auth.dto.AuthResponse;
import com.vego.backend.common.Tokens;
import com.vego.backend.config.AppProperties;
import com.vego.backend.security.JwtService;
import com.vego.backend.user.User;
import com.vego.backend.user.UserMapper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/** Cấp cặp token (access JWT + refresh token ngẫu nhiên) và thu hồi refresh token. */
@Service
public class TokenService {

    private final JwtService jwtService;
    private final RefreshTokenRepository refreshTokens;
    private final UserMapper userMapper;
    private final Duration refreshTtl;
    private final Clock clock;

    public TokenService(JwtService jwtService, RefreshTokenRepository refreshTokens, UserMapper userMapper,
                        AppProperties props, Clock clock) {
        this.jwtService = jwtService;
        this.refreshTokens = refreshTokens;
        this.userMapper = userMapper;
        this.refreshTtl = props.jwt().refreshTtl();
        this.clock = clock;
    }

    /** Cấp token mới. familyId = null nghĩa là phiên đăng nhập mới. */
    @Transactional
    public AuthResponse issue(User user, UUID familyId, String device) {
        Instant now = clock.instant();
        String refresh = Tokens.randomToken();
        refreshTokens.save(new RefreshToken(user, Tokens.sha256(refresh),
                familyId == null ? UUID.randomUUID() : familyId,
                truncate(device), now.plus(refreshTtl), now));
        return new AuthResponse(jwtService.createAccessToken(user), refresh, "Bearer",
                jwtService.accessTtlSeconds(), refreshTtl.toSeconds(), userMapper.toResponse(user));
    }

    @Transactional
    public void revokeAll(Long userId) {
        refreshTokens.revokeAllForUser(userId, clock.instant());
    }

    /** Dọn refresh token đã hết hạn hơn 7 ngày, chạy lúc 3 giờ sáng mỗi ngày. */
    @Scheduled(cron = "0 0 3 * * *", zone = "Asia/Ho_Chi_Minh")
    @Transactional
    public void cleanup() {
        refreshTokens.deleteExpiredBefore(clock.instant().minus(Duration.ofDays(7)));
    }

    private static String truncate(String device) {
        if (device == null || device.isBlank()) return null;
        return device.length() > 200 ? device.substring(0, 200) : device;
    }
}
