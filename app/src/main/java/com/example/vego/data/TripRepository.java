package com.example.vego.data;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Kho chuyến xe mẫu. Ở bước prototype dữ liệu nằm sẵn trong app;
 * khi có backend, lớp này sẽ gọi REST API thay vì trả danh sách cố định.
 */
public final class TripRepository {

    private static final String HCM = "TP. Hồ Chí Minh";
    private static final String DL = "Đà Lạt";
    private static final String MIEN_DONG = "Bến xe Miền Đông mới";
    private static final String MIEN_DONG_ADDR = "501 Hoàng Hữu Nam, TP. Thủ Đức";
    private static final String BX_DALAT = "Bến xe Đà Lạt";
    private static final String BX_DALAT_ADDR = "01 Tô Hiến Thành, TP. Đà Lạt";

    /** Thứ tự giống Figma: 3 chuyến đầu là 3 chuyến hiện trong thiết kế. */
    private static final List<Trip> TRIPS = Collections.unmodifiableList(Arrays.asList(
            new Trip("PT2200", "Phương Trang", "Limousine giường nằm 34 chỗ", Trip.BusType.LIMOUSINE,
                    4.8, 22 * 60, 7 * 60, 320_000, 12, HCM, DL,
                    MIEN_DONG, MIEN_DONG_ADDR, BX_DALAT, BX_DALAT_ADDR),
            new Trip("TB2100", "Thành Bưởi", "Limousine giường nằm 34 chỗ", Trip.BusType.LIMOUSINE,
                    4.8, 21 * 60, 7 * 60, 350_000, 8, HCM, DL,
                    "Văn phòng Thành Bưởi", "266 Lê Hồng Phong, Quận 5", BX_DALAT, BX_DALAT_ADDR),
            new Trip("PT2300", "Phương Trang", "Limousine giường nằm 34 chỗ", Trip.BusType.LIMOUSINE,
                    4.8, 23 * 60, 7 * 60, 320_000, 16, HCM, DL,
                    MIEN_DONG, MIEN_DONG_ADDR, BX_DALAT, BX_DALAT_ADDR),
            new Trip("DL2000", "Điền Linh", "Giường nằm 40 chỗ", Trip.BusType.SLEEPER,
                    4.6, 20 * 60, 7 * 60 + 30, 280_000, 20, HCM, DL,
                    MIEN_DONG, MIEN_DONG_ADDR, BX_DALAT, BX_DALAT_ADDR),
            new Trip("PT0800", "Phương Trang", "Limousine giường nằm 34 chỗ", Trip.BusType.LIMOUSINE,
                    4.9, 8 * 60, 7 * 60, 320_000, 5, HCM, DL,
                    MIEN_DONG, MIEN_DONG_ADDR, BX_DALAT, BX_DALAT_ADDR),
            new Trip("TB1330", "Thành Bưởi", "Ghế ngồi 29 chỗ", Trip.BusType.SEATER,
                    4.5, 13 * 60 + 30, 7 * 60 + 30, 220_000, 18, HCM, DL,
                    "Bến xe An Sương", "Quốc lộ 22, Hóc Môn", BX_DALAT, BX_DALAT_ADDR)
    ));

    private TripRepository() { }

    public static List<Trip> all() {
        return TRIPS;
    }

    public static Trip byId(String id) {
        for (Trip t : TRIPS) if (t.id.equals(id)) return t;
        return TRIPS.get(0);
    }

    /** Chuyến gợi ý ở Trang chủ (Phương Trang 22:00). */
    public static Trip suggested() {
        return TRIPS.get(0);
    }

    public static List<Trip> search(TripFilter filter) {
        List<Trip> result = new ArrayList<>();
        for (Trip t : TRIPS) if (filter == null || filter.matches(t)) result.add(t);
        return result;
    }

    /** Các nhà xe có trong dữ liệu, dùng cho màn Bộ lọc. */
    public static List<String> operators() {
        return Arrays.asList("Phương Trang", "Thành Bưởi", "Điền Linh");
    }

    /** Các điểm đón ở TP. Hồ Chí Minh. */
    public static final String[][] PICKUP_POINTS = {
            {MIEN_DONG, MIEN_DONG_ADDR},
            {"Bến xe An Sương", "Quốc lộ 22, Hóc Môn"},
            {"Văn phòng Phương Trang", "272 Đề Thám, Quận 1"}
    };

    /** Các điểm trả ở Đà Lạt. */
    public static final String[][] DROPOFF_POINTS = {
            {BX_DALAT, BX_DALAT_ADDR},
            {"Văn phòng Phương Trang Đà Lạt", "11A Lê Quý Đôn, TP. Đà Lạt"}
    };
}
