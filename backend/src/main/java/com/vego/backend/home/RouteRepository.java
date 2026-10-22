package com.vego.backend.home;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RouteRepository extends JpaRepository<Route, Integer> {

    List<Route> findByPopularRankNotNullOrderByPopularRankAsc();
}
