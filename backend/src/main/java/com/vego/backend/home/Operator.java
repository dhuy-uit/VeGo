package com.vego.backend.home;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Nhà xe. */
@Entity
@Table(name = "operators")
public class Operator {

    @Id
    private Integer id;
    private String code;
    private String name;
    private String hotline;

    public Integer getId() { return id; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public String getHotline() { return hotline; }
}
