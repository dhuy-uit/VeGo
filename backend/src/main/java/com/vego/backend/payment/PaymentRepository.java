package com.vego.backend.payment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    @Query("select p from Payment p join fetch p.booking where p.txnRef = :ref")
    Optional<Payment> findByTxnRef(@Param("ref") String ref);

    Optional<Payment> findFirstByBookingIdOrderByCreatedAtDesc(Long bookingId);
}
