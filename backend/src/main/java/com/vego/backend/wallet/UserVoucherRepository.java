package com.vego.backend.wallet;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface UserVoucherRepository extends JpaRepository<UserVoucher, Long> {

    @Query("select v from UserVoucher v where upper(v.code) = upper(:code)")
    Optional<UserVoucher> findByCode(@Param("code") String code);

    boolean existsByCode(String code);

    List<UserVoucher> findByUserIdOrderByCreatedAtDesc(Long userId);

    @Query("""
            select v from UserVoucher v
            where v.userId = :userId and v.status = com.vego.backend.wallet.UserVoucher.Status.AVAILABLE
              and v.expiresAt > :now
            order by v.discount desc, v.expiresAt asc
            """)
    List<UserVoucher> findUsable(@Param("userId") Long userId, @Param("now") Instant now);
}
