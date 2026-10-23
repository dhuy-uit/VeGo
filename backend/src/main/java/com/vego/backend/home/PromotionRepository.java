package com.vego.backend.home;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface PromotionRepository extends JpaRepository<Promotion, Integer> {

    @Query("""
            select p from Promotion p left join fetch p.route
            where p.active = true and p.showOnHome = true and p.startsAt <= :now and p.endsAt >= :now
            order by p.priority desc, p.endsAt asc
            """)
    List<Promotion> findRunning(@Param("now") Instant now);

    @Query("select p from Promotion p left join fetch p.route where upper(p.code) = upper(:code)")
    Optional<Promotion> findByCode(@Param("code") String code);
}
