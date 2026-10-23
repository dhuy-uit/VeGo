package com.vego.backend.wallet;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** Một dòng "Lịch sử giao dịch xu": số dương = nhận xu, số âm = dùng xu. */
@Entity
@Table(name = "coin_transactions")
public class CoinTransaction {

    public enum Source { WELCOME, CHECK_IN, GAME, BOOKING, VOUCHER, REFUND }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Source source;

    @Column(nullable = false)
    private int amount;

    @Column(length = 150)
    private String note;

    @Column(length = 40)
    private String ref;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected CoinTransaction() { }

    public CoinTransaction(Long userId, Source source, int amount, String note, String ref, Instant createdAt) {
        this.userId = userId;
        this.source = source;
        this.amount = amount;
        this.note = note;
        this.ref = ref;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public Source getSource() { return source; }
    public int getAmount() { return amount; }
    public String getNote() { return note; }
    public String getRef() { return ref; }
    public Instant getCreatedAt() { return createdAt; }
}
