package com.vego.backend.wallet;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CheckInRepository extends JpaRepository<CheckIn, Long> {

    Optional<CheckIn> findTopByUserIdOrderByDayDesc(Long userId);
}
