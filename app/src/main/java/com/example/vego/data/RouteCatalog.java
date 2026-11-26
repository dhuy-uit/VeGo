package com.example.vego.data;

import android.app.Activity;

import androidx.annotation.Nullable;

import com.example.vego.R;
import com.example.vego.common.ui.VeGoBanner;
import com.example.vego.common.ui.VeGoChoiceDialog;
import com.example.vego.data.api.ApiCallback;
import com.example.vego.data.api.ApiClient;
import com.example.vego.data.api.ApiModels;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

/**
 * Các tuyến VéGo đang bán, lấy từ GET /api/v1/routes. Người dùng chọn điểm đi / điểm đến từ danh sách
 * này thay vì gõ, nên không thể chọn nơi chưa có tuyến.
 */
public final class RouteCatalog {

    public interface OnPicked {
        void onPicked(String city);
    }

    @Nullable private static List<ApiModels.RoutePair> pairs;

    private RouteCatalog() { }

    /** Xóa bản lưu tạm (khi đổi máy chủ / đăng xuất không cần, nhưng để dành cho kiểm thử). */
    public static void clear() {
        pairs = null;
    }

    public static List<String> origins() {
        TreeSet<String> set = new TreeSet<>();
        if (pairs != null) for (ApiModels.RoutePair p : pairs) set.add(p.origin);
        return new ArrayList<>(set);
    }

    public static List<String> destinationsFrom(@Nullable String origin) {
        TreeSet<String> set = new TreeSet<>();
        if (pairs != null && origin != null) {
            for (ApiModels.RoutePair p : pairs) if (p.origin.equalsIgnoreCase(origin)) set.add(p.destination);
        }
        return new ArrayList<>(set);
    }

    /** Có tuyến origin → destination không (so sánh không phân biệt hoa thường). */
    public static boolean hasRoute(@Nullable String origin, @Nullable String destination) {
        if (pairs == null || origin == null || destination == null) return false;
        for (ApiModels.RoutePair p : pairs) {
            if (p.origin.equalsIgnoreCase(origin) && p.destination.equalsIgnoreCase(destination)) return true;
        }
        return false;
    }

    /** Tên thành phố đúng như server lưu (hoặc chính chuỗi đã cho nếu chưa tải được danh sách). */
    public static String canonical(String city) {
        if (pairs != null) {
            for (ApiModels.RoutePair p : pairs) {
                if (p.origin.equalsIgnoreCase(city)) return p.origin;
                if (p.destination.equalsIgnoreCase(city)) return p.destination;
            }
        }
        return city;
    }

    // ---------- Hộp chọn ----------

    public static void pickOrigin(Activity activity, @Nullable String current, OnPicked listener) {
        withRoutes(activity, () -> VeGoChoiceDialog.show(activity, activity.getString(R.string.pick_origin_title),
                origins(), current, listener::onPicked));
    }

    /** Chỉ liệt kê điểm đến có tuyến từ điểm đi đã chọn. */
    public static void pickDestination(Activity activity, @Nullable String origin, @Nullable String current,
                                       OnPicked listener) {
        if (origin == null || origin.trim().isEmpty()) {
            VeGoBanner.show(activity, activity.getString(R.string.pick_origin_first), VeGoBanner.Type.WARNING);
            return;
        }
        withRoutes(activity, () -> {
            List<String> options = destinationsFrom(origin);
            if (options.isEmpty()) {
                VeGoBanner.show(activity, activity.getString(R.string.pick_no_destination, origin),
                        VeGoBanner.Type.WARNING);
                return;
            }
            VeGoChoiceDialog.show(activity, activity.getString(R.string.pick_destination_title),
                    options, current, listener::onPicked);
        });
    }

    /** Có danh sách rồi thì chạy ngay, chưa có thì tải trước. */
    public static void withRoutes(Activity activity, Runnable action) {
        if (pairs != null) {
            action.run();
            return;
        }
        ApiClient.api().routes().enqueue(new ApiCallback<List<ApiModels.RoutePair>>(activity) {
            @Override
            protected void onSuccess(@Nullable List<ApiModels.RoutePair> body) {
                pairs = body == null ? new ArrayList<>() : body;
                action.run();
            }

            @Override
            protected void onError(ApiModels.ApiError error) {
                VeGoBanner.show(activity, error.message, VeGoBanner.Type.ERROR);
            }
        });
    }
}
