package com.vego.backend.admin;

import com.vego.backend.admin.AdminDtos.Activity;
import com.vego.backend.admin.AdminDtos.ActivityFeed;
import com.vego.backend.admin.AdminDtos.GroupStat;
import com.vego.backend.admin.AdminDtos.Highlight;
import com.vego.backend.admin.AdminDtos.ModelInfo;
import com.vego.backend.admin.AdminDtos.Overview;
import com.vego.backend.admin.AdminDtos.Point;
import com.vego.backend.admin.AdminDtos.RouteStat;
import com.vego.backend.admin.AdminDtos.SuggestionReport;
import com.vego.backend.admin.AdminDtos.TopRoutes;
import com.vego.backend.admin.AdminDtos.WeekPoint;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Số liệu dashboard. Mọi chỉ số tính theo NGÀY KHỞI HÀNH của chuyến:
 * doanh thu / vé bán = vé đã thanh toán (PAID) của các chuyến khởi hành trong kỳ;
 * ghế mở bán = tổng số ghế của các chuyến đã khởi hành trong kỳ; lấp đầy = vé bán / ghế mở bán.
 */
@Service
public class StatsService {

    private final AdminSupport db;
    /** Gợi ý tính theo quy tắc, "cập nhật" lần cuối khi server khởi động bản mới. */
    private final Instant startedAt;

    public StatsService(AdminSupport db) {
        this.db = db;
        this.startedAt = db.clock.instant();
    }

    private MapSqlParameterSource range(LocalDate from, LocalDate to) {
        return AdminSupport.params().addValue("from", Date.valueOf(from)).addValue("to", Date.valueOf(to))
                .addValue("tz", db.zone.getId()).addValue("now", Timestamp.from(db.now()));
    }

    /** Ghế mở bán + số chuyến đã khởi hành theo tuyến trong [from, to]. */
    private static final String CAPACITY_SQL = """
            SELECT t.route_id, count(*) AS trips, sum(t.total_seats) AS seats
            FROM trips t
            JOIN generate_series(CAST(:from AS date), CAST(:to AS date), interval '1 day') g
              ON (t.service_date IS NULL OR t.service_date = g::date)
            WHERE t.active AND ((g::date + t.depart_time) AT TIME ZONE :tz) <= :now
              AND g::date >= CAST(t.created_at AT TIME ZONE :tz AS date)
            GROUP BY t.route_id
            """;

    private static final String SOLD_SQL = """
            SELECT t.route_id, count(*) AS tickets, coalesce(sum(b.total), 0) AS revenue
            FROM bookings b JOIN trips t ON t.id = b.trip_id
            WHERE b.status = 'PAID' AND b.travel_date BETWEEN :from AND :to
            GROUP BY t.route_id
            """;

    private record Totals(long tickets, long revenue, long seats, long trips) { }

    private List<RouteStat> routeStats(LocalDate from, LocalDate to) {
        MapSqlParameterSource p = range(from, to);
        Map<Integer, long[]> cap = new HashMap<>();
        db.jdbc.query(CAPACITY_SQL, p, rs -> {
            cap.put(rs.getInt("route_id"), new long[]{rs.getLong("trips"), rs.getLong("seats")});
        });
        Map<Integer, long[]> sold = new HashMap<>();
        db.jdbc.query(SOLD_SQL, p, rs -> {
            sold.put(rs.getInt("route_id"), new long[]{rs.getLong("tickets"), rs.getLong("revenue")});
        });
        List<RouteStat> out = new ArrayList<>();
        db.jdbc.query("SELECT id, code, origin_city, destination_city FROM routes ORDER BY code", rs -> {
            int id = rs.getInt("id");
            long[] c = cap.getOrDefault(id, new long[]{0, 0});
            long[] s = sold.getOrDefault(id, new long[]{0, 0});
            if (c[1] == 0 && s[0] == 0) return;
            out.add(new RouteStat(0, id, rs.getString("code"),
                    rs.getString("origin_city") + " → " + rs.getString("destination_city"), s[0], c[1],
                    pct(s[0], c[1]), s[1]));
        });
        return out;
    }

    private Totals totals(LocalDate from, LocalDate to) {
        List<RouteStat> stats = routeStats(from, to);
        MapSqlParameterSource p = range(from, to);
        Long trips = db.jdbc.queryForObject("SELECT coalesce(sum(trips), 0) FROM (" + CAPACITY_SQL + ") c", p, Long.class);
        return new Totals(stats.stream().mapToLong(RouteStat::tickets).sum(),
                stats.stream().mapToLong(RouteStat::revenue).sum(),
                stats.stream().mapToLong(RouteStat::seats).sum(), trips == null ? 0 : trips);
    }

    private static double pct(long a, long b) {
        return b == 0 ? 0 : Math.round(1000.0 * a / b) / 10.0;
    }

    private static List<RouteStat> ranked(List<RouteStat> stats, String sort) {
        Comparator<RouteStat> c = switch (sort == null ? "" : sort.toUpperCase(Locale.ROOT)) {
            case "REVENUE" -> Comparator.comparingLong(RouteStat::revenue).reversed();
            case "FILL" -> Comparator.comparingDouble(RouteStat::fillRate).reversed();
            default -> Comparator.comparingLong(RouteStat::tickets).reversed();
        };
        List<RouteStat> sorted = stats.stream().sorted(c.thenComparing(RouteStat::code)).toList();
        List<RouteStat> out = new ArrayList<>();
        for (int i = 0; i < sorted.size(); i++) {
            RouteStat r = sorted.get(i);
            out.add(new RouteStat(i + 1, r.routeId(), r.code(), r.name(), r.tickets(), r.seats(), r.fillRate(),
                    r.revenue()));
        }
        return out;
    }

    private boolean includesDemo(LocalDate from, LocalDate to) {
        return db.exists("""
                SELECT 1 FROM bookings b JOIN users u ON u.id = b.user_id
                WHERE u.demo AND b.travel_date BETWEEN :from AND :to
                """, range(from, to));
    }

    // ---------- Tổng quan ----------

    /** mode: DAY (một ngày, chia theo khung 2 giờ), MONTH (theo ngày), YEAR (theo tháng). */
    @Transactional(readOnly = true)
    public Overview overview(String mode, LocalDate date) {
        LocalDate today = db.today();
        LocalDate d = date == null ? today : date;
        String m = mode == null ? "MONTH" : mode.toUpperCase(Locale.ROOT);
        LocalDate from;
        LocalDate to;
        LocalDate prevFrom;
        LocalDate prevTo;
        switch (m) {
            case "DAY" -> {
                from = d;
                to = d;
                prevFrom = d.minusDays(1);
                prevTo = prevFrom;
            }
            case "YEAR" -> {
                from = d.withDayOfYear(1);
                to = min(d.with(TemporalAdjusters.lastDayOfYear()), today);
                prevFrom = from.minusYears(1);
                prevTo = to.minusYears(1);
            }
            default -> {
                m = "MONTH";
                from = d.withDayOfMonth(1);
                to = min(d.with(TemporalAdjusters.lastDayOfMonth()), today);
                prevFrom = from.minusMonths(1);
                prevTo = min(to.minusMonths(1), prevFrom.with(TemporalAdjusters.lastDayOfMonth()));
            }
        }
        if (to.isBefore(from)) to = from;

        Totals cur = totals(from, to);
        Totals prev = totals(prevFrom, prevTo);
        List<Point> series = series(m, from, to);
        List<RouteStat> routes = ranked(routeStats(from, to), "TICKETS");
        long days = ChronoUnit.DAYS.between(from, to) + 1;
        return new Overview(m, from, to, cur.revenue(), prev.revenue(), cur.tickets(), prev.tickets(), cur.seats(),
                pct(cur.tickets(), cur.seats()), cur.trips(), Math.round(10.0 * cur.trips() / days) / 10.0,
                switch (m) { case "DAY" -> "HOUR"; case "YEAR" -> "MONTH"; default -> "DAY"; },
                series, routes, routes.size(), includesDemo(from, to));
    }

    private static LocalDate min(LocalDate a, LocalDate b) {
        return a.isBefore(b) ? a : b;
    }

    private List<Point> series(String mode, LocalDate from, LocalDate to) {
        MapSqlParameterSource p = range(from, to);
        Map<String, long[]> values = new HashMap<>();
        String sql = switch (mode) {
            case "DAY" -> """
                    SELECT lpad((extract(hour FROM t.depart_time)::int / 2 * 2)::text, 2, '0') AS k,
                           count(*) AS tickets, coalesce(sum(b.total), 0) AS revenue
                    FROM bookings b JOIN trips t ON t.id = b.trip_id
                    WHERE b.status = 'PAID' AND b.travel_date BETWEEN :from AND :to GROUP BY 1
                    """;
            case "YEAR" -> """
                    SELECT to_char(b.travel_date, 'MM') AS k, count(*) AS tickets, coalesce(sum(b.total), 0) AS revenue
                    FROM bookings b WHERE b.status = 'PAID' AND b.travel_date BETWEEN :from AND :to GROUP BY 1
                    """;
            default -> """
                    SELECT to_char(b.travel_date, 'DD') AS k, count(*) AS tickets, coalesce(sum(b.total), 0) AS revenue
                    FROM bookings b WHERE b.status = 'PAID' AND b.travel_date BETWEEN :from AND :to GROUP BY 1
                    """;
        };
        db.jdbc.query(sql, p, rs -> {
            values.put(rs.getString("k"), new long[]{rs.getLong("revenue"), rs.getLong("tickets")});
        });
        List<String> keys = new ArrayList<>();
        switch (mode) {
            case "DAY" -> {
                for (int h = 0; h < 24; h += 2) keys.add(String.format(Locale.ROOT, "%02d", h));
            }
            case "YEAR" -> {
                for (int mo = 1; mo <= 12; mo++) keys.add(String.format(Locale.ROOT, "%02d", mo));
            }
            default -> {
                for (LocalDate x = from; !x.isAfter(to); x = x.plusDays(1)) {
                    keys.add(String.format(Locale.ROOT, "%02d", x.getDayOfMonth()));
                }
            }
        }
        List<Point> out = new ArrayList<>();
        for (String k : keys) {
            long[] v = values.getOrDefault(k, new long[]{0, 0});
            String label = "DAY".equals(mode) ? k + "h" : "YEAR".equals(mode) ? "T" + Integer.parseInt(k) : k;
            out.add(new Point(label, v[0], v[1]));
        }
        return out;
    }

    // ---------- Top tuyến bán chạy ----------

    @Transactional(readOnly = true)
    public TopRoutes topRoutes(LocalDate fromDate, LocalDate toDate, String sort) {
        LocalDate today = db.today();
        LocalDate to = toDate == null ? today : min(toDate, today);
        LocalDate from = fromDate == null ? to.withDayOfMonth(1) : fromDate;
        if (from.isAfter(to)) from = to;
        String s = sort == null || sort.isBlank() ? "TICKETS" : sort.toUpperCase(Locale.ROOT);
        List<RouteStat> items = ranked(routeStats(from, to), s);
        long tickets = items.stream().mapToLong(RouteStat::tickets).sum();
        long seats = items.stream().mapToLong(RouteStat::seats).sum();
        long revenue = items.stream().mapToLong(RouteStat::revenue).sum();
        RouteStat leader = items.stream().max(Comparator.comparingLong(RouteStat::tickets)).orElse(null);
        Highlight highlight = leader == null ? null : highlight(leader.routeId(), from, to);
        return new TopRoutes(from, to, s, tickets, seats, revenue, tickets == 0 ? 0 : revenue / tickets, leader,
                items, highlight, includesDemo(from, to));
    }

    /** Chuyến lấp đầy cao nhất của tuyến dẫn đầu trong kỳ. */
    private Highlight highlight(int routeId, LocalDate from, LocalDate to) {
        MapSqlParameterSource p = range(from, to).addValue("routeId", routeId);
        List<Highlight> list = db.jdbc.query("""
                SELECT b.trip_id, b.travel_date, o.name AS operator, r.origin_city, r.destination_city,
                       t.depart_time, t.total_seats, count(*) AS sold
                FROM bookings b
                JOIN trips t ON t.id = b.trip_id
                JOIN routes r ON r.id = t.route_id
                JOIN operators o ON o.id = t.operator_id
                WHERE b.status = 'PAID' AND t.route_id = :routeId AND b.travel_date BETWEEN :from AND :to
                GROUP BY b.trip_id, b.travel_date, o.name, r.origin_city, r.destination_city, t.depart_time,
                         t.total_seats
                ORDER BY count(*)::numeric / t.total_seats DESC, count(*) DESC, b.travel_date DESC
                LIMIT 1
                """, p, (rs, i) -> {
            LocalDate date = rs.getDate("travel_date").toLocalDate();
            Instant dep = date.atTime(rs.getObject("depart_time", java.time.LocalTime.class)).atZone(db.zone)
                    .toInstant();
            long sold = rs.getLong("sold");
            int total = rs.getInt("total_seats");
            return new Highlight(ScheduleService.code(rs.getString("trip_id"), date), rs.getString("trip_id"), date,
                    rs.getString("operator"), rs.getString("origin_city") + " → " + rs.getString("destination_city"),
                    dep, sold, total, pct(sold, total));
        });
        return list.isEmpty() ? null : list.get(0);
    }

    // ---------- Báo cáo đề xuất ----------

    /**
     * Nhóm khách tại thời điểm sự kiện: RETURNING = có chuyến đã đi trong 90 ngày trước đó,
     * LAPSED = từng đi nhưng đã hơn 90 ngày, NEW = chưa đi chuyến nào (hoặc khách chưa đăng nhập).
     */
    private static final String EVENTS_SQL = """
            WITH ev AS (
                SELECT e.id, e.user_id, e.trip_id, e.kind, e.created_at,
                       (SELECT max(b.travel_date) FROM bookings b
                         WHERE b.user_id = e.user_id AND b.status = 'PAID'
                           AND b.travel_date < CAST(e.created_at AT TIME ZONE :tz AS date)) AS last_trip
                FROM suggestion_events e
                WHERE e.created_at >= :fromTs AND e.created_at < :toTs
            )
            SELECT ev.*,
                   CASE WHEN ev.last_trip IS NULL THEN 'NEW'
                        WHEN ev.last_trip >= CAST(ev.created_at AT TIME ZONE :tz AS date) - 90 THEN 'RETURNING'
                        ELSE 'LAPSED' END AS grp
            FROM ev
            """;

    /** Vé "từ đề xuất": cùng người, cùng chuyến, đặt trong 48 giờ sau một lượt nhấp (lấy lượt nhấp gần nhất). */
    private static final String CONVERSIONS_SQL = """
            SELECT grp, count(*) AS bookings, coalesce(sum(total), 0) AS revenue
            FROM (
                SELECT DISTINCT ON (b.id) b.id, b.total, e.grp
                FROM bookings b
                JOIN (""" + EVENTS_SQL + """
                ) e ON e.kind = 'CLICK' AND e.user_id = b.user_id AND e.trip_id = b.trip_id
                   AND e.created_at <= b.created_at AND e.created_at > b.created_at - interval '48 hours'
                WHERE b.status = 'PAID' AND b.created_at >= :fromTs AND b.created_at < :toTs
                ORDER BY b.id, e.created_at DESC
            ) x
            GROUP BY grp
            """;

    private static final Map<String, String> GROUPS = new LinkedHashMap<>();

    static {
        GROUPS.put("RETURNING", "Khách quay lại");
        GROUPS.put("NEW", "Khách mới");
        GROUPS.put("LAPSED", "Khách chưa đi gần đây");
    }

    private MapSqlParameterSource tsRange(LocalDate from, LocalDate to) {
        return AdminSupport.params().addValue("tz", db.zone.getId())
                .addValue("fromTs", Timestamp.from(from.atStartOfDay(db.zone).toInstant()))
                .addValue("toTs", Timestamp.from(to.plusDays(1).atStartOfDay(db.zone).toInstant()));
    }

    private record Counts(Map<String, long[]> byGroup) {
        long[] sum(String group) {
            long[] out = new long[4];
            byGroup.forEach((g, v) -> {
                if (group == null || group.equals(g)) for (int i = 0; i < 4; i++) out[i] += v[i];
            });
            return out;
        }
    }

    /** [impressions, clicks, bookings, revenue] theo nhóm khách. */
    private Counts counts(LocalDate from, LocalDate to) {
        MapSqlParameterSource p = tsRange(from, to);
        Map<String, long[]> m = new HashMap<>();
        GROUPS.keySet().forEach(g -> m.put(g, new long[4]));
        db.jdbc.query("SELECT grp, kind, count(*) AS n FROM (" + EVENTS_SQL + ") e GROUP BY grp, kind", p, rs -> {
            long[] v = m.get(rs.getString("grp"));
            if (v == null) return;
            if ("IMPRESSION".equals(rs.getString("kind"))) v[0] += rs.getLong("n");
            else v[1] += rs.getLong("n");
        });
        db.jdbc.query(CONVERSIONS_SQL, p, rs -> {
            long[] v = m.get(rs.getString("grp"));
            if (v == null) return;
            v[2] += rs.getLong("bookings");
            v[3] += rs.getLong("revenue");
        });
        return new Counts(m);
    }

    @Transactional(readOnly = true)
    public SuggestionReport suggestions(LocalDate fromDate, LocalDate toDate, String group) {
        LocalDate today = db.today();
        LocalDate to = toDate == null ? today : min(toDate, today);
        LocalDate from = fromDate == null ? to.withDayOfMonth(1) : fromDate;
        if (from.isAfter(to)) from = to;
        String g = group == null || group.isBlank() || "ALL".equalsIgnoreCase(group) ? null
                : group.toUpperCase(Locale.ROOT);
        long days = ChronoUnit.DAYS.between(from, to) + 1;

        Counts cur = counts(from, to);
        Counts prev = counts(from.minusDays(days), from.minusDays(1));
        long[] c = cur.sum(g);
        long[] pv = prev.sum(g);
        Totals totals = totals(from, to);

        List<GroupStat> groups = new ArrayList<>();
        GROUPS.forEach((key, label) -> {
            long[] v = cur.byGroup().get(key);
            groups.add(new GroupStat(key, label, v[0], v[1], v[2], pct(v[1], v[0])));
        });

        // 12 tuần gần nhất (kết thúc ở tuần chứa ngày "to")
        LocalDate lastWeek = to.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate firstWeek = lastWeek.minusWeeks(11);
        MapSqlParameterSource p = tsRange(firstWeek, to);
        Map<LocalDate, long[]> weeks = new LinkedHashMap<>();
        for (int i = 0; i < 12; i++) weeks.put(firstWeek.plusWeeks(i), new long[2]);
        String filter = g == null ? "" : " WHERE grp = :grp";
        p.addValue("grp", g);
        db.jdbc.query("""
                SELECT CAST(date_trunc('week', created_at AT TIME ZONE :tz) AS date) AS wk, kind, count(*) AS n
                FROM (""" + EVENTS_SQL + ") e" + filter + " GROUP BY 1, 2", p, rs -> {
            long[] v = weeks.get(rs.getDate("wk").toLocalDate());
            if (v == null) return;
            if ("IMPRESSION".equals(rs.getString("kind"))) v[0] += rs.getLong("n");
            else v[1] += rs.getLong("n");
        });
        List<WeekPoint> weekly = new ArrayList<>();
        weeks.forEach((wk, v) -> weekly.add(new WeekPoint(wk, v[0], v[1], pct(v[1], v[0]))));

        ModelInfo model = new ModelInfo("Gợi ý \"Dành riêng cho bạn\"", "rule-v1", "ACTIVE", 90, startedAt,
                "Dựa trên chuyến đã đi gần nhất: cùng tuyến, cùng nhà xe, cùng buổi khởi hành, còn ghế; "
                        + "khách chưa có lịch sử nhận chuyến đánh giá cao của tuyến phổ biến nhất. "
                        + "Tính trực tiếp mỗi lần mở app, không cần huấn luyện lại.");
        boolean demo = db.exists("""
                SELECT 1 FROM suggestion_events e JOIN users u ON u.id = e.user_id
                WHERE u.demo AND e.created_at >= :fromTs AND e.created_at < :toTs
                """, tsRange(from, to));
        return new SuggestionReport(from, to, g == null ? "ALL" : g, c[0], pv[0], c[1], pct(c[1], c[0]), c[2],
                pct(c[2], c[1]), c[3], pct(c[3], totals.revenue()), weekly, groups, model, demo);
    }

    // ---------- Hoạt động gần đây (chuông thông báo) ----------

    @Transactional(readOnly = true)
    public ActivityFeed activity() {
        Instant now = db.now();
        MapSqlParameterSource p = AdminSupport.params().addValue("since", Timestamp.from(now.minus(7, ChronoUnit.DAYS)))
                .addValue("day", Timestamp.from(now.minus(1, ChronoUnit.DAYS))).addValue("now", Timestamp.from(now));
        List<Activity> items = db.jdbc.query("""
                SELECT * FROM (
                    SELECT 'BOOKING' AS kind, b.paid_at AS at, 'Vé mới ' || b.code AS title,
                           u.full_name || ' · ' || b.trip_id || ' · ' || to_char(b.travel_date, 'DD/MM') AS detail
                    FROM bookings b JOIN users u ON u.id = b.user_id
                    WHERE b.status IN ('PAID', 'CANCELLED') AND b.paid_at >= :since AND b.paid_at <= :now AND NOT u.demo
                    UNION ALL
                    SELECT 'CANCEL', b.cancelled_at, 'Hủy vé ' || b.code,
                           u.full_name || ' · hoàn ' || coalesce(b.refund_amount, 0) || 'đ'
                    FROM bookings b JOIN users u ON u.id = b.user_id
                    WHERE b.status = 'CANCELLED' AND b.cancelled_at >= :since AND b.cancelled_at <= :now AND NOT u.demo
                    UNION ALL
                    SELECT 'REVIEW', r.created_at, 'Đánh giá ' || r.rating || '★ chuyến ' || r.trip_id,
                           u.full_name || coalesce(' · ' || left(r.comment, 80), '')
                    FROM reviews r JOIN users u ON u.id = r.user_id
                    WHERE r.created_at >= :since AND r.created_at <= :now AND NOT u.demo
                    UNION ALL
                    SELECT 'USER', u.created_at, 'Khách mới đăng ký', u.full_name
                    FROM users u WHERE u.created_at >= :since AND u.created_at <= :now AND NOT u.demo
                      AND u.role = 'CUSTOMER'
                ) x ORDER BY at DESC LIMIT 15
                """, p, (rs, i) -> new Activity(rs.getString("kind"), rs.getString("title"), rs.getString("detail"),
                rs.getTimestamp("at").toInstant()));
        long last24h = items.stream().filter(a -> a.at().isAfter(now.minus(1, ChronoUnit.DAYS))).count();
        return new ActivityFeed(last24h, items);
    }
}
