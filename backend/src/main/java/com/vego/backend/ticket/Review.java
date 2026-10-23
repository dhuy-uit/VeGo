package com.vego.backend.ticket;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;

/** Đánh giá nhà xe sau chuyến đi (mỗi vé một đánh giá, sửa lại được). */
@Entity
@Table(name = "reviews")
public class Review {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "booking_id", nullable = false, unique = true)
    private Long bookingId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "trip_id", nullable = false)
    private String tripId;

    @Column(name = "operator_id", nullable = false)
    private Integer operatorId;

    @Column(nullable = false)
    private short rating;

    /** Các nhãn, cách nhau bởi dấu phẩy (CLEAN, ONTIME, STAFF). */
    @Column(length = 200)
    private String tags;

    @Column(length = 1000)
    private String comment;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Review() { }

    public Review(Long bookingId, Long userId, String tripId, Integer operatorId) {
        this.bookingId = bookingId;
        this.userId = userId;
        this.tripId = tripId;
        this.operatorId = operatorId;
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

    public Long getId() { return id; }
    public Long getBookingId() { return bookingId; }
    public Long getUserId() { return userId; }
    public String getTripId() { return tripId; }
    public Integer getOperatorId() { return operatorId; }
    public int getRating() { return rating; }
    public void setRating(int rating) { this.rating = (short) rating; }
    public String getTags() { return tags; }
    public void setTags(String tags) { this.tags = tags; }
    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
