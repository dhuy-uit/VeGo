package com.vego.backend.trip;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Collection;

/**
 * Ghi lượt hiển thị / lượt nhấp của mục "Dành riêng cho bạn" (số liệu cho "Báo cáo đề xuất" trên web admin).
 * Lỗi ghi số liệu không được làm hỏng màn hình của người dùng nên chỉ bỏ qua.
 */
@Component
public class SuggestionEvents {

    private static final String INSERT = """
            INSERT INTO suggestion_events (user_id, trip_id, kind)
            SELECT ?, t.id, ? FROM trips t WHERE upper(t.id) = upper(?)
            """;

    private final JdbcTemplate jdbc;

    public SuggestionEvents(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void impressions(Long userId, Collection<String> tripIds) {
        for (String id : tripIds) record(userId, id, "IMPRESSION");
    }

    /** true nếu chuyến tồn tại và đã ghi nhận. */
    public boolean click(Long userId, String tripId) {
        return record(userId, tripId, "CLICK");
    }

    private boolean record(Long userId, String tripId, String kind) {
        if (tripId == null || tripId.isBlank()) return false;
        try {
            return jdbc.update(INSERT, userId, kind, tripId.trim()) > 0;
        } catch (RuntimeException e) {
            return false;
        }
    }
}
