package com.vego.backend.admin;

import com.vego.backend.common.ApiException;
import com.vego.backend.common.Phones;
import com.vego.backend.config.AppProperties;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Pattern;

/** Tiện ích chung cho các service web admin: CSDL, giờ Việt Nam, phân trang, sinh mã. */
@Component
public class AdminSupport {

    private static final Pattern MARKS = Pattern.compile("\\p{M}+");

    final NamedParameterJdbcTemplate jdbc;
    final Clock clock;
    final ZoneId zone;

    public AdminSupport(NamedParameterJdbcTemplate jdbc, Clock clock, AppProperties props) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.zone = props.zone();
    }

    Instant now() {
        return clock.instant();
    }

    LocalDate today() {
        return LocalDate.now(clock.withZone(zone));
    }

    static MapSqlParameterSource params() {
        return new MapSqlParameterSource();
    }

    /** Bỏ dấu + viết thường để tìm kiếm không phân biệt dấu. */
    static String fold(String s) {
        if (s == null) return "";
        String n = Normalizer.normalize(s, Normalizer.Form.NFD).replace('đ', 'd').replace('Đ', 'D');
        return MARKS.matcher(n).replaceAll("").toLowerCase(Locale.ROOT).trim();
    }

    static boolean matches(String query, String... fields) {
        String q = fold(query);
        if (q.isEmpty()) return true;
        for (String f : fields) {
            if (f != null && fold(f).contains(q)) return true;
        }
        return false;
    }

    static <T> AdminDtos.Page<T> page(List<T> all, Integer page, Integer size) {
        int s = size == null || size < 1 ? 10 : Math.min(size, 100);
        int p = page == null || page < 1 ? 1 : page;
        int from = Math.min(all.size(), (p - 1) * s);
        int to = Math.min(all.size(), from + s);
        return new AdminDtos.Page<>(List.copyOf(all.subList(from, to)), p, s, all.size());
    }

    static String trimToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    static String normalizePhone(String raw) {
        String phone = Phones.normalize(raw);
        if (phone == null) throw ApiException.field("INVALID_PHONE", "phone", "Số điện thoại cần 10 chữ số");
        return phone;
    }

    /** Mã kế tiếp dạng PREFIX + 3 chữ số (XE025, TX021, T006). */
    String nextCode(String table, String prefix) {
        Integer max = jdbc.queryForObject(
                "SELECT max(CAST(substring(code FROM :len) AS INT)) FROM " + table
                        + " WHERE code ~ :pattern",
                params().addValue("len", prefix.length() + 1).addValue("pattern", "^" + prefix + "[0-9]+$"),
                Integer.class);
        return prefix + String.format(Locale.ROOT, "%03d", (max == null ? 0 : max) + 1);
    }

    boolean exists(String sql, MapSqlParameterSource p) {
        Boolean b = jdbc.queryForObject("SELECT EXISTS (" + sql + ")", p, Boolean.class);
        return Boolean.TRUE.equals(b);
    }

    static ApiException conflict(String code, String message) {
        return new ApiException(HttpStatus.CONFLICT, code, message);
    }

    static ApiException badRequest(String code, String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, code, message);
    }

    static ApiException errors(List<String> errors) {
        return new ApiException(HttpStatus.CONFLICT, "SCHEDULE_CONFLICT", String.join(" · ", errors));
    }

    /** Hạng GPLX -> số chỗ tối đa được chở (Luật TTATGTĐB 2024, tính cả ghế lái). */
    static int maxSeats(String licenseClass) {
        if (licenseClass == null) return 0;
        return switch (licenseClass) {
            case "D1", "D1E" -> 16;
            case "D2", "D2E" -> 29;
            case "D", "DE" -> 99;
            default -> 0;
        };
    }

    static String typeLabel(String busType, int seats) {
        String base = switch (busType) {
            case "LIMOUSINE" -> seats >= 20 ? "Limousine giường nằm" : "Limousine";
            case "SLEEPER" -> "Giường nằm";
            default -> "Ghế ngồi";
        };
        return base + " " + seats + " chỗ";
    }

    static <K, V> V getOr(Map<K, V> map, K key, Function<K, V> fallback) {
        V v = map.get(key);
        return v != null ? v : fallback.apply(key);
    }
}
