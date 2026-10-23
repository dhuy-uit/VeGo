package com.vego.backend.notification;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;

/** Một thông báo trong app (màn "Thông báo"). */
@Entity
@Table(name = "notifications")
public class Notification {

    /** REMINDER: nhắc lịch, TICKET: vé, PROMO: khuyến mãi, REVIEW: đánh giá, WALLET: Ví xu. */
    public enum Kind { REMINDER, TICKET, PROMO, REVIEW, WALLET }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Kind kind;

    @Column(nullable = false, length = 150)
    private String title;

    @Column(nullable = false, length = 500)
    private String body;

    @Column(name = "ticket_code", length = 20)
    private String ticketCode;

    @Column(name = "read_at")
    private Instant readAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Notification() { }

    public Notification(Long userId, Kind kind, String title, String body, String ticketCode) {
        this.userId = userId;
        this.kind = kind;
        this.title = title;
        this.body = body;
        this.ticketCode = ticketCode;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public Kind getKind() { return kind; }
    public String getTitle() { return title; }
    public String getBody() { return body; }
    public String getTicketCode() { return ticketCode; }
    public Instant getReadAt() { return readAt; }
    public void setReadAt(Instant readAt) { this.readAt = readAt; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
