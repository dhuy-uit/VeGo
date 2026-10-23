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

/** Voucher người dùng đã đổi bằng xu; nhập mã ở màn Thanh toán để dùng (1 lần). */
@Entity
@Table(name = "user_vouchers")
public class UserVoucher {

    public enum Status { AVAILABLE, USED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 30)
    private String code;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "offer_id", nullable = false)
    private String offerId;

    @Column(nullable = false)
    private int discount;

    @Column(name = "min_order", nullable = false)
    private int minOrder;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.AVAILABLE;

    @Column(name = "booking_id")
    private Long bookingId;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected UserVoucher() { }

    public UserVoucher(String code, Long userId, VoucherOffer offer, Instant createdAt, Instant expiresAt) {
        this.code = code;
        this.userId = userId;
        this.offerId = offer.getId();
        this.discount = offer.getDiscount();
        this.minOrder = offer.getMinOrder();
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    public boolean usableAt(Instant now) {
        return status == Status.AVAILABLE && expiresAt.isAfter(now);
    }

    public void markUsed(Long bookingId, Instant at) {
        this.status = Status.USED;
        this.bookingId = bookingId;
        this.usedAt = at;
    }

    /** Tên hiển thị, vd "Voucher Ví xu giảm 20.000đ". */
    public String title() {
        return "Voucher Ví xu giảm " + CoinLedger.coins(discount) + "đ";
    }

    public Long getId() { return id; }
    public String getCode() { return code; }
    public Long getUserId() { return userId; }
    public String getOfferId() { return offerId; }
    public int getDiscount() { return discount; }
    public int getMinOrder() { return minOrder; }
    public Status getStatus() { return status; }
    public Long getBookingId() { return bookingId; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getUsedAt() { return usedAt; }
    public Instant getCreatedAt() { return createdAt; }
}
