package com.example.vego.data;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Gợi ý chuyến cá nhân hóa (Figma: "Khám phá · Đề xuất chuyến").
 * Dựa trên chuyến đã đi gần nhất: cùng tuyến, cùng nhà xe, cùng buổi khởi hành.
 * Bước prototype tính ngay trong app; sau này sẽ do backend (RAG + lịch sử đặt vé) trả về.
 */
public final class SuggestionEngine {

    /** Kết quả gợi ý: 1 chuyến chính + các lựa chọn thêm. */
    public static final class Result {
        /** Chuyến đã đi làm căn cứ gợi ý (null nếu chưa có lịch sử). */
        @Nullable public final Ticket basedOn;
        public final Trip main;
        public final List<Trip> more;

        Result(@Nullable Ticket basedOn, Trip main, List<Trip> more) {
            this.basedOn = basedOn;
            this.main = main;
            this.more = more;
        }
    }

    /** Số lựa chọn thêm tối đa (Figma hiện 1). */
    private static final int MAX_MORE = 2;

    private SuggestionEngine() { }

    public static Result suggest() {
        Ticket last = lastCompleted();
        List<Trip> candidates = new ArrayList<>();

        if (last != null) {
            for (Trip t : TripRepository.all()) {
                if (t.origin.equals(last.origin) && t.destination.equals(last.destination)
                        && t.operator.equals(last.operator)
                        && slotOf(t.departMinutes) == slotOf(last.departMinutes)) {
                    candidates.add(t);
                }
            }
            // Giờ gần với giờ đã đi nhất lên trước
            final int target = last.departMinutes;
            candidates.sort((a, b) -> Integer.compare(
                    Math.abs(a.departMinutes - target), Math.abs(b.departMinutes - target)));
        }

        if (candidates.isEmpty()) {
            // Chưa có lịch sử: gợi ý các chuyến đánh giá cao nhất
            candidates.addAll(TripRepository.all());
            candidates.sort((a, b) -> Double.compare(b.rating, a.rating));
            last = null;
        }

        Trip main = candidates.get(0);
        List<Trip> more = new ArrayList<>();
        for (int i = 1; i < candidates.size() && more.size() < MAX_MORE; i++) more.add(candidates.get(i));
        return new Result(last, main, more);
    }

    @Nullable
    private static Ticket lastCompleted() {
        for (Ticket t : TicketStore.get().history()) {
            if (t.status == Ticket.Status.COMPLETED) return t; // history() đã xếp mới nhất trước
        }
        return null;
    }

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
