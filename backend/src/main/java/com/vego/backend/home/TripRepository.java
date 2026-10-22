package com.vego.backend.home;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface TripRepository extends JpaRepository<Trip, String> {

    /** Các chuyến đang chạy của những tuyến cho trước, nạp sẵn nhà xe / bến để tránh truy vấn N+1. */
    @Query("""
            select t from Trip t
              join fetch t.route r
              join fetch t.operator
              join fetch t.pickupStation
              join fetch t.dropoffStation
            where t.active = true and r.id in :routeIds
            """)
    List<Trip> findActiveByRouteIds(@Param("routeIds") Collection<Integer> routeIds);
}
