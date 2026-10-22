package com.vego.backend.auth;

import com.vego.backend.common.ApiException;
import com.vego.backend.config.AppProperties;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Chống dò mật khẩu: sai quá N lần trong khoảng thời gian khóa -> tạm khóa đăng nhập tài khoản đó.
 * Lưu trong bộ nhớ (đủ cho 1 server).
 */
@Service
public class LoginAttemptService {

    private record Attempt(int failures, Instant firstFailure, Instant lockedUntil) { }

    private final Map<String, Attempt> attempts = new ConcurrentHashMap<>();
    private final int maxFailures;
    private final Duration lockout;
    private final Clock clock;

    public LoginAttemptService(AppProperties props, Clock clock) {
        this.maxFailures = props.auth().maxLoginFailures();
        this.lockout = props.auth().lockout();
        this.clock = clock;
    }

    public void checkAllowed(String key) {
        Attempt a = attempts.get(key);
        Instant now = clock.instant();
        if (a != null && a.lockedUntil() != null && a.lockedUntil().isAfter(now)) {
            long minutes = Math.max(1, Duration.between(now, a.lockedUntil()).toMinutes() + 1);
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY_ATTEMPTS",
                    "Bạn đã nhập sai quá " + maxFailures + " lần. Vui lòng thử lại sau " + minutes + " phút");
        }
    }

    public void recordFailure(String key) {
        Instant now = clock.instant();
        attempts.compute(key, (k, a) -> {
            if (a == null || a.firstFailure().plus(lockout).isBefore(now)) {
                a = new Attempt(0, now, null);
            }
            int failures = a.failures() + 1;
            Instant lockedUntil = failures >= maxFailures ? now.plus(lockout) : null;
            return new Attempt(failures, a.firstFailure(), lockedUntil);
        });
    }

    public void reset(String key) {
        attempts.remove(key);
    }
}
