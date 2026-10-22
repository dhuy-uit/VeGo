package com.vego.backend.home;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Tuyến đường. popularRank khác null thì hiện ở mục "Tuyến phổ biến". */
@Entity
@Table(name = "routes")
public class Route {

    @Id
    private Integer id;

    @Column(name = "origin_city")
    private String originCity;

    @Column(name = "destination_city")
    private String destinationCity;

    @Column(name = "distance_km")
    private Integer distanceKm;

    @Column(name = "popular_rank")
    private Integer popularRank;

    @Column(name = "image_key")
    private String imageKey;

    public Integer getId() { return id; }
    public String getOriginCity() { return originCity; }
    public String getDestinationCity() { return destinationCity; }
    public Integer getDistanceKm() { return distanceKm; }
    public Integer getPopularRank() { return popularRank; }
    public String getImageKey() { return imageKey; }
}
