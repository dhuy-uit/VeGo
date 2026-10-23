package com.vego.backend.wallet;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CoinTransactionRepository extends JpaRepository<CoinTransaction, Long> {

    List<CoinTransaction> findByUserIdOrderByCreatedAtDescIdDesc(Long userId, Pageable page);

    long countByUserId(Long userId);

    /** Cộng / trừ số dư nguyên tử; trả về 0 nếu trừ quá số dư (không cho âm). */
    @Modifying
    @Query(value = "update users set coin_balance = coin_balance + :delta where id = :userId and coin_balance + :delta >= 0",
            nativeQuery = true)
    int adjustBalance(@Param("userId") Long userId, @Param("delta") int delta);

    @Query(value = "select coin_balance from users where id = :userId", nativeQuery = true)
    Integer balanceOf(@Param("userId") Long userId);
}
