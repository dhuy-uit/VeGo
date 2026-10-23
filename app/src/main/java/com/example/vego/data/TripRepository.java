package com.example.vego.data;

import androidx.annotation.Nullable;

import com.example.vego.data.api.ApiModels;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Kết quả tìm chuyến gần nhất (GET /api/v1/trips/search), giữ trong bộ nhớ để màn Danh sách và
 * Bộ lọc lọc / sắp xếp ngay trên máy mà không phải gọi lại server mỗi lần chạm chip.
 */
public final class TripRepository {

    private static List<Trip> trips = Collections.emptyList();
    private static List<String> operators = Collections.emptyList();
    @Nullable private static String key;

    private TripRepository() { }

    /** Lưu kết quả tìm kiếm mới. */
    public static void setResults(ApiModels.TripSearch result) {
        List<Trip> list = new ArrayList<>();
        if (result.trips != null) for (ApiModels.TripDto t : result.trips) list.add(Trip.from(t));
        trips = Collections.unmodifiableList(list);
        operators = result.operators == null ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(result.operators));
        key = result.origin + "|" + result.destination + "|" + result.date;
    }

    /** Khóa của lần tìm gần nhất: "điểm đi|điểm đến|yyyy-MM-dd". */
    @Nullable
    public static String key() {
        return key;
    }

    public static List<Trip> all() {
        return trips;
    }

    @Nullable
    public static Trip byId(String id) {
        for (Trip t : trips) if (t.id.equals(id)) return t;
        return null;
    }

    public static List<Trip> search(@Nullable TripFilter filter) {
        List<Trip> result = new ArrayList<>();
        for (Trip t : trips) if (filter == null || filter.matches(t)) result.add(t);
        return result;
    }

    /** Các nhà xe chạy tuyến vừa tìm, dùng cho màn Bộ lọc. */
    public static List<String> operators() {
        return operators;
    }
}
