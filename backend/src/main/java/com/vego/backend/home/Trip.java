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
import java.time.LocalDate;
import java.time.LocalTime;

/** Chuyến xe: chạy cố định hằng ngày, hoặc chuyến tăng cường chỉ chạy một ngày (serviceDate khác null). */
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

    /** null = chạy hằng ngày; có giá trị = chỉ chạy đúng ngày đó (tạo từ web admin "Lập lịch chuyến"). */
    @Column(name = "service_date")
    private LocalDate serviceDate;

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
    public LocalDate getServiceDate() { return serviceDate; }

    /** Chuyến cố định hằng ngày (không phải chuyến tăng cường). */
    public boolean isRecurring() { return serviceDate == null; }

    /** Chuyến có chạy vào ngày này không. */
    public boolean runsOn(LocalDate date) { return serviceDate == null || serviceDate.equals(date); }
}
