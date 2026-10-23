package com.vego.backend.booking;

import com.vego.backend.home.Trip;
import com.vego.backend.user.User;
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
import java.time.LocalDate;

/** Một đơn đặt vé (1 ghế). Giữ chỗ HOLDING trong vài phút, thanh toán xong thành PAID. */
@Entity
@Table(name = "bookings")
public class Booking {

    public enum Status { HOLDING, PAID, EXPIRED, CANCELLED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 20)
    private String code;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "trip_id")
    private Trip trip;

    @Column(name = "travel_date", nullable = false)
    private LocalDate travelDate;

    @Column(name = "seat_code", nullable = false, length = 10)
    private String seatCode;

    @Column(name = "passenger_name", nullable = false, length = 100)
    private String passengerName;

    @Column(name = "passenger_phone", nullable = false, length = 15)
    private String passengerPhone;

    @Column(name = "pickup_label", nullable = false, length = 200)
    private String pickupLabel;

    @Column(name = "dropoff_label", nullable = false, length = 200)
    private String dropoffLabel;

    @Column(nullable = false)
    private int fare;

    @Column(nullable = false)
    private int discount;

    @Column(nullable = false)
    private int total;

    @Column(name = "voucher_code", length = 30)
    private String voucherCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.HOLDING;

    @Column(name = "payment_method", length = 20)
    private String paymentMethod;

    @Column(name = "hold_expires_at", nullable = false)
    private Instant holdExpiresAt;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public boolean isHoldingAt(Instant now) {
        return status == Status.HOLDING && holdExpiresAt.isAfter(now);
    }

    /** Áp / bỏ mã giảm giá: tính lại tổng tiền. */
    public void applyDiscount(String code, int amount) {
        this.voucherCode = code;
        this.discount = amount;
        this.total = fare - amount;
    }

    public Long getId() { return id; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
    public Trip getTrip() { return trip; }
    public void setTrip(Trip trip) { this.trip = trip; }
    public LocalDate getTravelDate() { return travelDate; }
    public void setTravelDate(LocalDate travelDate) { this.travelDate = travelDate; }
    public String getSeatCode() { return seatCode; }
    public void setSeatCode(String seatCode) { this.seatCode = seatCode; }
    public String getPassengerName() { return passengerName; }
    public void setPassengerName(String passengerName) { this.passengerName = passengerName; }
    public String getPassengerPhone() { return passengerPhone; }
    public void setPassengerPhone(String passengerPhone) { this.passengerPhone = passengerPhone; }
    public String getPickupLabel() { return pickupLabel; }
    public void setPickupLabel(String pickupLabel) { this.pickupLabel = pickupLabel; }
    public String getDropoffLabel() { return dropoffLabel; }
    public void setDropoffLabel(String dropoffLabel) { this.dropoffLabel = dropoffLabel; }
    public int getFare() { return fare; }
    public void setFare(int fare) { this.fare = fare; this.total = fare - discount; }
    public int getDiscount() { return discount; }
    public int getTotal() { return total; }
    public String getVoucherCode() { return voucherCode; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public String getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }
    public Instant getHoldExpiresAt() { return holdExpiresAt; }
    public void setHoldExpiresAt(Instant holdExpiresAt) { this.holdExpiresAt = holdExpiresAt; }
    public Instant getPaidAt() { return paidAt; }
    public void setPaidAt(Instant paidAt) { this.paidAt = paidAt; }
    public Instant getCreatedAt() { return createdAt; }
}
