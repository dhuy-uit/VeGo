package com.vego.backend.booking;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    @Query("""
            select b from Booking b
              join fetch b.trip t join fetch t.route join fetch t.operator
              join fetch t.pickupStation join fetch t.dropoffStation
            where b.code = :code
            """)
    Optional<Booking> findByCodeWithTrip(@Param("code") String code);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from Booking b where b.id = :id")
    Optional<Booking> lockById(@Param("id") Long id);

    @Query("""
            select b from Booking b
              join fetch b.trip t join fetch t.route join fetch t.operator
              join fetch t.pickupStation join fetch t.dropoffStation
            where b.user.id = :userId
            order by b.createdAt desc
            """)
    List<Booking> findByUser(@Param("userId") Long userId);

    boolean existsByCode(String code);

    /** Ghế đã có đơn còn hiệu lực (đang giữ chưa hết hạn hoặc đã thanh toán) chưa. */
    @Query("""
            select count(b) > 0 from Booking b
            where b.trip.id = :tripId and b.travelDate = :date and b.seatCode = :seat and b.id <> :exceptId
              and (b.status = com.vego.backend.booking.Booking.Status.PAID
                   or (b.status = com.vego.backend.booking.Booking.Status.HOLDING and b.holdExpiresAt > :now))
            """)
    boolean seatTaken(@Param("tripId") String tripId, @Param("date") LocalDate date, @Param("seat") String seat,
                      @Param("exceptId") Long exceptId, @Param("now") Instant now);

    @Query("""
            select b.seatCode from Booking b
            where b.trip.id = :tripId and b.travelDate = :date
              and (b.status = com.vego.backend.booking.Booking.Status.PAID
                   or (b.status = com.vego.backend.booking.Booking.Status.HOLDING and b.holdExpiresAt > :now))
            """)
    List<String> takenSeats(@Param("tripId") String tripId, @Param("date") LocalDate date, @Param("now") Instant now);

    /** Đơn đang giữ đã quá hạn -> EXPIRED (giải phóng ghế). */
    @Modifying
    @Query("""
            update Booking b set b.status = com.vego.backend.booking.Booking.Status.EXPIRED, b.updatedAt = :now
            where b.status = com.vego.backend.booking.Booking.Status.HOLDING and b.holdExpiresAt <= :now
            """)
    int expireHolds(@Param("now") Instant now);

    /** Mỗi người chỉ giữ một đơn tại một thời điểm: tạo đơn mới thì hủy đơn đang giữ cũ. */
    @Modifying
    @Query("""
            update Booking b set b.status = com.vego.backend.booking.Booking.Status.CANCELLED, b.updatedAt = :now
            where b.user.id = :userId and b.status = com.vego.backend.booking.Booking.Status.HOLDING
            """)
    int cancelHoldsOfUser(@Param("userId") Long userId, @Param("now") Instant now);

    @Query("""
            select count(b) from Booking b
            where b.user.id = :userId and upper(b.voucherCode) = upper(:code)
              and b.status = com.vego.backend.booking.Booking.Status.PAID
            """)
    long countPaidWithVoucher(@Param("userId") Long userId, @Param("code") String code);
}
