package com.vego.backend.home;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Bến xe / văn phòng đón trả khách. */
@Entity
@Table(name = "stations")
public class Station {

    @Id
    private Integer id;
    private String city;
    private String name;
    private String address;

    public Integer getId() { return id; }
    public String getCity() { return city; }
    public String getName() { return name; }
    public String getAddress() { return address; }
}
