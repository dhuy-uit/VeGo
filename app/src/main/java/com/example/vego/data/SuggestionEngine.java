package com.example.vego.data;

/**
 * Tiện ích cho màn "Dành riêng cho bạn". Việc chọn chuyến gợi ý đã chuyển lên server
 * (GET /api/v1/suggestions: cùng tuyến, cùng nhà xe, cùng buổi với chuyến đã đi gần nhất).
 */
public final class SuggestionEngine {

    private SuggestionEngine() { }

    /** 0 = sáng (trước 12h), 1 = chiều (12–18h), 2 = tối (từ 18h). */
    public static int slotOf(int minutes) {
        if (minutes < 12 * 60) return 0;
        if (minutes < 18 * 60) return 1;
        return 2;
    }

    public static String slotName(int minutes) {
        switch (slotOf(minutes)) {
            case 0: return "buổi sáng";
            case 1: return "buổi chiều";
            default: return "buổi tối";
        }
    }
}
