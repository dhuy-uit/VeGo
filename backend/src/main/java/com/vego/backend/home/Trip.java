package com.vego.backend.home;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalTime;

/** Chuyến xe chạy cố định hằng ngày. */
@Entity
@Table(name = "trips")
public class Trip {

    public enum BusType { LIMOUSINE, SLEEPER, SEATER }

    @Id
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "route_id")
    private Route route;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "operator_id")
    private Operator operator;

    @Column(name = "bus_name")
    private String busName;

    @Enumerated(EnumType.STRING)
    @Column(name = "bus_type")
    private BusType busType;

    private BigDecimal rating;

    @Column(name = "depart_time")
    private LocalTime departTime;

    @Column(name = "duration_minutes")
    private int durationMinutes;

    private int price;

    @Column(name = "total_seats")
    private int totalSeats;

    @Column(name = "seats_left")
    private int seatsLeft;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pickup_station_id")
    private Station pickupStation;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "dropoff_station_id")
    private Station dropoffStation;

    private boolean active;

    public String getId() { return id; }
    public Route getRoute() { return route; }
    public Operator getOperator() { return operator; }
    public String getBusName() { return busName; }
    public BusType getBusType() { return busType; }
    public BigDecimal getRating() { return rating; }
    public LocalTime getDepartTime() { return departTime; }
    public int getDurationMinutes() { return durationMinutes; }
    public int getPrice() { return price; }
    public int getTotalSeats() { return totalSeats; }
    public int getSeatsLeft() { return seatsLeft; }
    public Station getPickupStation() { return pickupStation; }
    public Station getDropoffStation() { return dropoffStation; }
    public boolean isActive() { return active; }
}
