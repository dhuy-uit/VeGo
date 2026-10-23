package com.vego.backend.trip;

import com.vego.backend.home.Station;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StationRepository extends JpaRepository<Station, Integer> {

    List<Station> findByCityOrderByIdAsc(String city);

    List<Station> findAllByOrderByCityAscIdAsc();
}
