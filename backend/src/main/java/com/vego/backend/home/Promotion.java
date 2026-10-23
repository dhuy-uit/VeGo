package com.vego.backend.home;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

/** Chương trình khuyến mãi hiện ở banner Trang chủ. */
@Entity
@Table(name = "promotions")
public class Promotion {

    @Id
    private Integer id;
    private String code;
    private String badge;
    private String title;
    private String description;
    private String note;

    @Column(name = "discount_percent")
    private Integer discountPercent;

    @Column(name = "discount_amount")
    private Integer discountAmount;

    @Column(name = "min_order")
    private int minOrder;

    @Column(name = "per_user_limit")
    private int perUserLimit;

    @Column(name = "show_on_home")
    private boolean showOnHome;

    @Column(name = "max_discount")
    private Integer maxDiscount;

    @Column(name = "image_key")
    private String imageKey;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "route_id")
    private Route route;

    @Column(name = "starts_at")
    private Instant startsAt;

    @Column(name = "ends_at")
    private Instant endsAt;

    private int priority;
    private boolean active;

    public Integer getId() { return id; }
    public String getCode() { return code; }
    public String getBadge() { return badge; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public String getNote() { return note; }
    public Integer getDiscountPercent() { return discountPercent; }
    public Integer getDiscountAmount() { return discountAmount; }
    public int getMinOrder() { return minOrder; }
    public int getPerUserLimit() { return perUserLimit; }
    public boolean isShowOnHome() { return showOnHome; }
    public Integer getMaxDiscount() { return maxDiscount; }
    public String getImageKey() { return imageKey; }
    public Route getRoute() { return route; }
    public Instant getStartsAt() { return startsAt; }
    public Instant getEndsAt() { return endsAt; }
    public int getPriority() { return priority; }
    public boolean isActive() { return active; }
}
