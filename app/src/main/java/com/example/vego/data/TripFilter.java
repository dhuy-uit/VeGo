package com.example.vego.data;

import java.util.HashSet;
import java.util.Set;

/** Điều kiện lọc chuyến xe (màn "Bộ lọc chuyến"). Tập rỗng = không lọc theo tiêu chí đó. */
public class TripFilter {

    /** 4 khung giờ: 0 = 00–06, 1 = 06–12, 2 = 12–18, 3 = 18–24. */
    public final Set<Integer> timeSlots = new HashSet<>();
    public final Set<String> operators = new HashSet<>();
    public final Set<Trip.BusType> busTypes = new HashSet<>();
    public int minPrice = PRICE_MIN;
    public int maxPrice = PRICE_MAX;

    public static final int PRICE_MIN = 100_000;
    public static final int PRICE_MAX = 500_000;
    public static final int PRICE_STEP = 10_000;

    /**
     * Bộ lọc gợi ý lần đầu mở (giống thiết kế Figma: buổi tối, Phương Trang, Limousine),
     * chỉ giữ tiêu chí có chuyến phù hợp trên tuyến vừa tìm.
     */
    public static TripFilter suggested() {
        TripFilter f = new TripFilter();
        f.timeSlots.add(3);
        if (TripRepository.operators().contains("Phương Trang")) f.operators.add("Phương Trang");
        f.busTypes.add(Trip.BusType.LIMOUSINE);
        if (TripRepository.search(f).isEmpty()) return new TripFilter();
        return f;
    }

    public TripFilter copy() {
        TripFilter f = new TripFilter();
        f.timeSlots.addAll(timeSlots);
        f.operators.addAll(operators);
        f.busTypes.addAll(busTypes);
        f.minPrice = minPrice;
        f.maxPrice = maxPrice;
        return f;
    }

    public boolean isEmpty() {
        return timeSlots.isEmpty() && operators.isEmpty() && busTypes.isEmpty()
                && minPrice == PRICE_MIN && maxPrice == PRICE_MAX;
    }

    public boolean matches(Trip t) {
        if (!timeSlots.isEmpty() && !timeSlots.contains(t.departMinutes / (6 * 60))) return false;
        if (!operators.isEmpty() && !operators.contains(t.operator)) return false;
        if (!busTypes.isEmpty() && !busTypes.contains(t.busType)) return false;
        return t.price >= minPrice && t.price <= maxPrice;
    }
}
