package com.vego.backend.payment;

import com.vego.backend.booking.Booking;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;

/** Một lần thanh toán cho một đơn (thanh toán lỗi có thể thử lại -> nhiều Payment / đơn). */
@Entity
@Table(name = "payments")
public class Payment {

    public enum Provider { MOMO, VNPAY, SIMULATED }

    /** Phương thức người dùng chọn trong app. */
    public enum Method { MOMO, VNPAY, CARD }

    public enum Status { PENDING, SUCCESS, FAILED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id")
    private Booking booking;

    @Column(name = "txn_ref", nullable = false, unique = true, length = 40)
    private String txnRef;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Provider provider;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Method method;

    @Column(nullable = false)
    private int amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.PENDING;

    @Column(name = "provider_txn_id", length = 50)
    private String providerTxnId;

    @Column(name = "response_code", length = 20)
    private String responseCode;

    @Column(length = 255)
    private String message;

    @Column(name = "pay_url")
    private String payUrl;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Payment() { }

    public Payment(Booking booking, String txnRef, Provider provider, Method method, int amount) {
        this.booking = booking;
        this.txnRef = txnRef;
        this.provider = provider;
        this.method = method;
        this.amount = amount;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public void complete(boolean success, String providerTxnId, String responseCode, String message) {
        this.status = success ? Status.SUCCESS : Status.FAILED;
        this.providerTxnId = providerTxnId;
        this.responseCode = responseCode;
        this.message = message == null ? null : message.length() > 255 ? message.substring(0, 255) : message;
    }

    public Long getId() { return id; }
    public Booking getBooking() { return booking; }
    public String getTxnRef() { return txnRef; }
    public Provider getProvider() { return provider; }
    public Method getMethod() { return method; }
    public int getAmount() { return amount; }
    public Status getStatus() { return status; }
    public String getMessage() { return message; }
    public String getResponseCode() { return responseCode; }
    public String getPayUrl() { return payUrl; }
    public void setPayUrl(String payUrl) { this.payUrl = payUrl; }
    public Instant getCreatedAt() { return createdAt; }
}
