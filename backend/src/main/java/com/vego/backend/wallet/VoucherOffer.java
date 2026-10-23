package com.vego.backend.wallet;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Voucher đổi được bằng xu (vd giảm 20.000đ cho đơn từ 200.000đ, giá 500 xu). */
@Entity
@Table(name = "voucher_offers")
public class VoucherOffer {

    @Id
    private String id;

    private int discount;

    @Column(name = "min_order")
    private int minOrder;

    private int cost;

    @Column(name = "valid_days")
    private int validDays;

    @Column(name = "sort_order")
    private int sortOrder;

    private boolean active;

    public String getId() { return id; }
    public int getDiscount() { return discount; }
    public int getMinOrder() { return minOrder; }
    public int getCost() { return cost; }
    public int getValidDays() { return validDays; }
    public int getSortOrder() { return sortOrder; }
    public boolean isActive() { return active; }
}
