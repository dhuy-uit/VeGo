package com.vego.backend.auth;

import com.vego.backend.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "refresh_tokens")
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "family_id", nullable = false)
    private UUID familyId;

    @Column(length = 200)
    private String device;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected RefreshToken() { }

    public RefreshToken(User user, String tokenHash, UUID familyId, String device, Instant expiresAt, Instant now) {
        this.user = user;
        this.tokenHash = tokenHash;
        this.familyId = familyId;
        this.device = device;
        this.expiresAt = expiresAt;
        this.createdAt = now;
    }

    public boolean isRevoked() { return revokedAt != null; }

    public boolean isExpired(Instant now) { return !expiresAt.isAfter(now); }

    public void revoke(Instant now) {
        if (revokedAt == null) revokedAt = now;
    }

    public Long getId() { return id; }
    public User getUser() { return user; }
    public UUID getFamilyId() { return familyId; }
    public String getDevice() { return device; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getRevokedAt() { return revokedAt; }
}
