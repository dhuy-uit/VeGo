package com.vego.backend.wallet;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;

/** Một lần điểm danh trong ngày. */
@Entity
@Table(name = "check_ins")
public class CheckIn {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false)
    private LocalDate day;

    /** Số ngày điểm danh liên tiếp tính tới ngày này. */
    @Column(nullable = false)
    private int streak;

    /** Ngày thứ mấy trong chu kỳ 7 ngày (1..7). */
    @Column(name = "cycle_day", nullable = false)
    private int cycleDay;

    @Column(nullable = false)
    private int reward;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected CheckIn() { }

    public CheckIn(Long userId, LocalDate day, int streak, int cycleDay, int reward, Instant createdAt) {
        this.userId = userId;
        this.day = day;
        this.streak = streak;
        this.cycleDay = cycleDay;
        this.reward = reward;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public LocalDate getDay() { return day; }
    public int getStreak() { return streak; }
    public int getCycleDay() { return cycleDay; }
    public int getReward() { return reward; }
    public Instant getCreatedAt() { return createdAt; }
}
