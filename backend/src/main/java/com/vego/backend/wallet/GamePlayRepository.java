package com.vego.backend.wallet;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Optional;

public interface GamePlayRepository extends JpaRepository<GamePlay, Long> {

    long countByUserIdAndDay(Long userId, LocalDate day);

    @Query("select coalesce(max(g.score), 0) from GamePlay g where g.userId = :userId and g.finishedAt is not null")
    int bestScore(@Param("userId") Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select g from GamePlay g where g.id = :id and g.userId = :userId")
    Optional<GamePlay> lockOwned(@Param("id") Long id, @Param("userId") Long userId);
}
