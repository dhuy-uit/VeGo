package com.vego.backend.wallet;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;

/** Một lượt chơi "Khủng long phiêu lưu". */
@Entity
@Table(name = "game_plays")
public class GamePlay {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false)
    private LocalDate day;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    private Integer score;

    private Integer coins;

    protected GamePlay() { }

    public GamePlay(Long userId, LocalDate day, Instant startedAt) {
        this.userId = userId;
        this.day = day;
        this.startedAt = startedAt;
    }

    public void finish(Instant at, int score, int coins) {
        this.finishedAt = at;
        this.score = score;
        this.coins = coins;
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public LocalDate getDay() { return day; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getFinishedAt() { return finishedAt; }
    public Integer getScore() { return score; }
    public Integer getCoins() { return coins; }
}
