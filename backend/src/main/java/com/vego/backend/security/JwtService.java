package com.vego.backend.security;

import com.vego.backend.config.AppProperties;
import com.vego.backend.user.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;

/** Tạo và kiểm tra access token (JWT ký HS256). */
@Service
public class JwtService {

    private final AppProperties.Jwt config;
    private final SecretKey key;
    private final Clock clock;

    public JwtService(AppProperties props, Clock clock) {
        this.config = props.jwt();
        byte[] secret = config.secret().getBytes(StandardCharsets.UTF_8);
        if (secret.length < 32) {
            throw new IllegalStateException("JWT_SECRET phải dài tối thiểu 32 ký tự");
        }
        this.key = Keys.hmacShaKeyFor(secret);
        this.clock = clock;
    }

    public String createAccessToken(User user) {
        Instant now = clock.instant();
        return Jwts.builder()
                .issuer(config.issuer())
                .subject(String.valueOf(user.getId()))
                .claim("email", user.getEmail())
                .claim("role", user.getRole().name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(config.accessTtl())))
                .signWith(key)
                .compact();
    }

    /** Ném ExpiredJwtException nếu hết hạn, JwtException nếu sai chữ ký / sai định dạng. */
    public AuthUser parse(String token) throws JwtException {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .requireIssuer(config.issuer())
                .clock(() -> Date.from(clock.instant()))
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return new AuthUser(Long.parseLong(claims.getSubject()),
                claims.get("email", String.class),
                claims.get("role", String.class));
    }

    public long accessTtlSeconds() {
        return config.accessTtl().toSeconds();
    }
}
