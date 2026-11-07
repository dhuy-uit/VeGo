package com.vego.backend.admin;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Dữ liệu minh họa cho dashboard web admin (bật bằng app.admin-demo.enabled, mặc định bật):
 * <ul>
 *   <li>Khách mẫu (users.demo = true, không đăng nhập được) và vé đã đi của 365 ngày gần nhất,
 *       tỷ lệ lấp đầy thay đổi theo tuyến, mùa, ngày trong tuần và tăng dần theo thời gian.</li>
 *   <li>Đánh giá, lượt hiển thị / nhấp mục "Dành riêng cho bạn" trong 90 ngày.</li>
 *   <li>Phân công tài xế cho các chuyến 7 ngày tới.</li>
 * </ul>
 * Chỉ thêm phần còn thiếu (ngày chưa có vé mẫu) nên chạy lại nhiều lần không bị trùng; job hằng ngày bổ sung ngày mới.
 * Vé mẫu chỉ có ở các ngày đã qua nên không ảnh hưởng ghế trống khi khách đặt vé trong app.
 */
@Component
@Order(3)
public class AdminDemoData implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminDemoData.class);
    static final int DEMO_USERS = 6000;

    private final AdminSupport db;
    private final PasswordEncoder encoder;
    private final TransactionTemplate tx;
    private final boolean enabled;
    private final int days;

    public AdminDemoData(AdminSupport db, PasswordEncoder encoder, TransactionTemplate tx,
                         @Value("${app.admin-demo.enabled:true}") boolean enabled,
                         @Value("${app.admin-demo.days:365}") int days) {
        this.db = db;
        this.encoder = encoder;
        this.tx = tx;
        this.enabled = enabled;
        this.days = days;
    }

    @Override
    public void run(ApplicationArguments args) {
        topUp();
    }

    /** 00:20 mỗi ngày: thêm vé mẫu của ngày hôm qua và phân công tài xế cho ngày mới. */
    @Scheduled(cron = "0 20 0 * * *", zone = "Asia/Ho_Chi_Minh")
    public void daily() {
        topUp();
    }

    public void topUp() {
        if (!enabled) return;
        try {
            long started = System.currentTimeMillis();
            Integer added = tx.execute(s -> seed());
            log.info("Dữ liệu minh họa web admin: thêm {} vé mẫu ({} ms)", added, System.currentTimeMillis() - started);
        } catch (RuntimeException e) {
            log.warn("Không tạo được dữ liệu minh họa web admin: {}", e.getMessage(), e);
        }
    }

    private int seed() {
        LocalDate today = db.today();
        LocalDate yesterday = today.minusDays(1);
        ensureUsers();

        LocalDate last = db.jdbc.queryForObject("""
                SELECT max(b.travel_date) FROM bookings b JOIN users u ON u.id = b.user_id WHERE u.demo
                """, AdminSupport.params(), LocalDate.class);
        LocalDate from = last == null ? today.minusDays(days) : last.plusDays(1);
        int added = 0;
        if (!from.isAfter(yesterday)) {
            MapSqlParameterSource p = AdminSupport.params()
                    .addValue("from", Date.valueOf(from)).addValue("to", Date.valueOf(yesterday))
                    .addValue("start", Date.valueOf(today.minusDays(days)))
                    .addValue("tz", db.zone.getId()).addValue("users", DEMO_USERS);
            added = db.jdbc.update(BOOKINGS_SQL, p);
            db.jdbc.update(REVIEWS_SQL, p);
            p.addValue("eventsFrom", Timestamp.from(max(from, today.minusDays(90)).atStartOfDay(db.zone).toInstant()))
                    .addValue("eventsTo", Timestamp.from(today.atStartOfDay(db.zone).toInstant()));
            db.jdbc.update(CLICKS_FROM_BOOKINGS_SQL, p);
            db.jdbc.update(EXTRA_EVENTS_SQL, p);
        }
        assignDrivers(today);
        return added;
    }

    private static LocalDate max(LocalDate a, LocalDate b) {
        return a.isAfter(b) ? a : b;
    }

    private void ensureUsers() {
        Integer have = db.jdbc.queryForObject("SELECT count(*) FROM users WHERE demo", AdminSupport.params(),
                Integer.class);
        if (have != null && have >= DEMO_USERS) return;
        // Mật khẩu ngẫu nhiên không ai biết: tài khoản mẫu không đăng nhập được
        String hash = encoder.encode(UUID.randomUUID() + "-" + UUID.randomUUID());
        db.jdbc.update(USERS_SQL, AdminSupport.params().addValue("hash", hash).addValue("users", DEMO_USERS)
                .addValue("start", Date.valueOf(db.today().minusDays(days + 30))));
    }

    /** Tài xế cố định cho từng chuyến cố định (đúng hạng GPLX), 7 ngày tới, không ghi đè phân công có sẵn. */
    private void assignDrivers(LocalDate today) {
        List<String[]> pairs = List.of(
                new String[]{"PT2200", "TX001"}, new String[]{"TB2100", "TX002"}, new String[]{"PT2300", "TX003"},
                new String[]{"DL2000", "TX005"}, new String[]{"PT0800", "TX007"}, new String[]{"TB1330", "TX006"},
                new String[]{"PT2130", "TX008"}, new String[]{"HL2000", "TX009"}, new String[]{"PT0900", "TX012"},
                new String[]{"PT0700", "TX013"}, new String[]{"PT1500", "TX010"}, new String[]{"PT0630", "TX014"},
                new String[]{"TB1700", "TX015"}, new String[]{"PT2215", "TX017"});
        for (String[] pair : pairs) {
            db.jdbc.update("""
                    INSERT INTO trip_assignments (trip_id, travel_date, vehicle_id, driver_id)
                    SELECT t.id, g::date, t.vehicle_id, d.id
                    FROM trips t
                    JOIN drivers d ON d.code = :driver AND d.status = 'READY' AND d.license_expiry > g::date
                    CROSS JOIN generate_series(CAST(:from AS date), CAST(:to AS date), interval '1 day') g
                    WHERE t.id = :trip AND t.active AND t.service_date IS NULL
                    ON CONFLICT (trip_id, travel_date) DO NOTHING
                    """, AdminSupport.params().addValue("driver", pair[1]).addValue("trip", pair[0])
                    .addValue("from", Date.valueOf(today.minusDays(1))).addValue("to", Date.valueOf(today.plusDays(6))));
        }
    }

    // ---------------------------------------------------------------------------------------------
    // SQL (đã thử trên PostgreSQL 16: 365 ngày x 14 chuyến ~ 120.000 vé trong vài giây)
    // ---------------------------------------------------------------------------------------------

    static final String USERS_SQL = """
            INSERT INTO users (full_name, phone, email, password_hash, role, status, demo, created_at, updated_at,
                               password_changed_at)
            SELECT (ARRAY['Nguyễn','Trần','Lê','Phạm','Hoàng','Huỳnh','Phan','Vũ','Võ','Đặng','Bùi','Đỗ','Hồ','Ngô',
                          'Dương','Lý'])[1 + (i * 7) % 16]
                   || ' ' || (ARRAY['Văn','Thị','Minh','Ngọc','Quốc','Thanh','Gia','Hoài','Đức','Kim','Bảo','Thu'])
                          [1 + (i * 5) % 12]
                   || ' ' || (ARRAY['An','Bình','Châu','Dũng','Giang','Hà','Hải','Hạnh','Hiếu','Hòa','Khánh','Lan',
                          'Linh','Long','Mai','Nam','Nhung','Phúc','Quân','Quyên','Sơn','Tâm','Thảo','Trang','Tuấn',
                          'Uyên','Vy','Yến','Khoa','Ngân','Phát'])[1 + (i * 11) % 31],
                   '0100' || lpad(i::text, 6, '0'), 'khach' || lpad(i::text, 4, '0') || '@demo.vego.vn', :hash,
                   'CUSTOMER', 'ACTIVE', TRUE,
                   CAST(:start AS date) + (i % 300) * interval '1 day', now(), now()
            FROM generate_series(1, :users) i
            ON CONFLICT DO NOTHING
            """;

    /**
     * Vé mẫu cho mỗi chuyến x ngày còn trống trong [from, to]. Lấp đầy = nền theo tuyến x mùa x thứ trong tuần
     * x tăng trưởng (82% -> 100% trong kỳ) x nhiễu cố định theo (chuyến, ngày). Ghế chọn ngẫu nhiên cố định,
     * khách chọn lệch về một nhóm khách quen (khách quen đi nhiều, đa số khách đi vài lần). 3% vé đã hủy.
     */
    static final String BOOKINGS_SQL = """
            WITH u AS (
                SELECT array_agg(id ORDER BY id) AS ids, array_agg(full_name ORDER BY id) AS names,
                       array_agg(phone ORDER BY id) AS phones, count(*) AS n
                FROM users WHERE demo
            ),
            td AS (
                SELECT t.id AS trip_id, t.total_seats, t.bus_type, t.price, t.depart_time, g::date AS travel_date,
                       ps.name AS pickup, ds.name AS dropoff,
                       greatest(0.15, least(0.97,
                           (CASE r.code WHEN 'T001' THEN 0.80 WHEN 'T002' THEN 0.74 WHEN 'T003' THEN 0.66
                                        WHEN 'T004' THEN 0.70 ELSE 0.70 END)
                         * (CASE extract(month FROM g) WHEN 1 THEN 1.12 WHEN 2 THEN 1.15 WHEN 6 THEN 1.08
                                WHEN 7 THEN 1.12 WHEN 8 THEN 1.08 WHEN 9 THEN 0.92 WHEN 10 THEN 0.95
                                WHEN 12 THEN 1.06 ELSE 1.0 END)
                         * (CASE extract(isodow FROM g) WHEN 5 THEN 1.10 WHEN 6 THEN 1.12 WHEN 7 THEN 1.08
                                WHEN 2 THEN 0.88 WHEN 3 THEN 0.90 ELSE 1.0 END)
                         * (0.82 + 0.18 * (g::date - CAST(:start AS date))::numeric
                                   / greatest(1, CAST(:to AS date) - CAST(:start AS date)))
                         * (0.85 + 0.30 * ('x' || substr(md5(t.id || g::date), 1, 8))::bit(32)::bigint::numeric
                                   / 4294967295)
                       )) AS fill
                FROM trips t
                JOIN routes r ON r.id = t.route_id
                JOIN stations ps ON ps.id = t.pickup_station_id
                JOIN stations ds ON ds.id = t.dropoff_station_id
                CROSS JOIN generate_series(CAST(:from AS date), CAST(:to AS date), interval '1 day') g
                WHERE t.active AND (t.service_date IS NULL OR t.service_date = g::date)
                  AND NOT EXISTS (SELECT 1 FROM bookings b WHERE b.trip_id = t.id AND b.travel_date = g::date)
            ),
            seats AS (
                SELECT td.*, n,
                       CASE WHEN td.bus_type <> 'SEATER' AND td.total_seats >= 20 AND n > (td.total_seats + 1) / 2
                            THEN 'B' || lpad((n - (td.total_seats + 1) / 2)::text, 2, '0')
                            ELSE 'A' || lpad(n::text, 2, '0') END AS seat,
                       row_number() OVER (PARTITION BY td.trip_id, td.travel_date
                                          ORDER BY md5(td.trip_id || td.travel_date || n)) AS rn,
                       ('x' || substr(md5(td.trip_id || td.travel_date || n || 'u'), 1, 8))::bit(32)::bigint AS h,
                       ('x' || substr(md5(td.trip_id || td.travel_date || n || 'u'), 9, 8))::bit(32)::bigint AS h2
                FROM td CROSS JOIN LATERAL generate_series(1, td.total_seats) n
            ),
            picked AS (
                SELECT s.*, ((s.travel_date + s.depart_time) AT TIME ZONE :tz) AS depart_at,
                       s.h % 1000 AS r1, (s.h / 1000) % 1000 AS r2,
                       -- khách: bình phương phân phối đều -> chỉ số nhỏ (khách quen) được chọn nhiều hơn
                       floor(power((s.h2 % 1000003)::numeric / 1000003, 2.2) * (SELECT n FROM u))::int AS ui
                FROM seats s
                WHERE s.rn <= round(s.total_seats * s.fill)
            )
            INSERT INTO bookings (code, user_id, trip_id, travel_date, seat_code, passenger_name, passenger_phone,
                                  pickup_label, dropoff_label, fare, discount, total, voucher_code, status,
                                  payment_method, hold_expires_at, paid_at, created_at, updated_at, cancelled_at,
                                  refund_amount, coins_earned, reminder_sent, review_invited)
            SELECT 'VD' || to_char(p.travel_date, 'DDMMYY') || p.trip_id || p.seat,
                   u.ids[1 + p.ui], p.trip_id, p.travel_date, p.seat, u.names[1 + p.ui], u.phones[1 + p.ui],
                   p.pickup, p.dropoff, p.price, x.discount, p.price - x.discount,
                   CASE WHEN x.discount > 0 THEN 'VEGO10' END,
                   CASE WHEN p.r1 < 30 THEN 'CANCELLED' ELSE 'PAID' END,
                   (ARRAY['MOMO', 'VNPAY', 'CARD'])[1 + p.r2 % 3],
                   x.paid + interval '2 minutes', x.paid, x.paid - interval '3 minutes', x.paid,
                   CASE WHEN p.r1 < 30 THEN x.paid + (p.depart_at - x.paid) / 2 END,
                   CASE WHEN p.r1 < 30 THEN (p.price - x.discount) * 8 / 10 END,
                   0, TRUE, TRUE
            FROM picked p
            CROSS JOIN u
            CROSS JOIN LATERAL (
                SELECT p.depart_at - make_interval(hours => 2 + ((p.h / 7) % 240)::int) AS paid,
                       CASE WHEN p.r2 < 120 THEN least(30000, p.price / 10) ELSE 0 END AS discount
            ) x
            """;

    /** ~9% vé đã đi có đánh giá, phần lớn 4–5 sao. */
    static final String REVIEWS_SQL = """
            INSERT INTO reviews (booking_id, user_id, trip_id, operator_id, rating, tags, comment, created_at, updated_at)
            SELECT b.id, b.user_id, b.trip_id, t.operator_id,
                   (ARRAY[5,5,5,5,5,4,4,4,3,2])[1 + (b.id % 10)],
                   (ARRAY['CLEAN,ONTIME', 'STAFF', 'COMFORT,CLEAN', 'ONTIME', NULL])[1 + (b.id / 10 % 5)],
                   (ARRAY['Xe sạch, tài xế chạy êm.', 'Đúng giờ, nhân viên nhiệt tình.', NULL,
                          'Giường rộng, ngủ ngon cả chuyến.', 'Xe đến trễ khoảng 20 phút.', NULL])[1 + (b.id / 7 % 6)],
                   ((b.travel_date + t.depart_time) AT TIME ZONE :tz) + make_interval(mins => t.duration_minutes + 180),
                   now()
            FROM bookings b
            JOIN trips t ON t.id = b.trip_id
            JOIN users u ON u.id = b.user_id AND u.demo
            WHERE b.status = 'PAID' AND b.travel_date BETWEEN :from AND :to AND b.id % 11 = 0
            ON CONFLICT (booking_id) DO NOTHING
            """;

    /**
     * Vé "từ đề xuất": 6% vé trong 90 ngày gần nhất có lượt hiển thị + nhấp trước khi đặt (1–30 giờ).
     */
    static final String CLICKS_FROM_BOOKINGS_SQL = """
            INSERT INTO suggestion_events (user_id, trip_id, kind, created_at)
            SELECT b.user_id, b.trip_id, k.kind, b.created_at - make_interval(mins => 60 + (b.id % 1740)::int + k.gap)
            FROM bookings b
            JOIN users u ON u.id = b.user_id AND u.demo
            CROSS JOIN (VALUES ('IMPRESSION', 2), ('CLICK', 0)) AS k(kind, gap)
            WHERE b.created_at >= :eventsFrom AND b.created_at < :eventsTo AND b.status = 'PAID'
              AND b.travel_date BETWEEN :from AND :to
              AND (b.id * 7919) % 100 < 6
            """;

    /**
     * Lượt hiển thị / nhấp không dẫn tới đặt vé, mỗi ngày: tỷ lệ nhấp tăng dần từ ~9% lên ~15%,
     * khoảng 20% lượt hiển thị là khách chưa đăng nhập.
     */
    static final String EXTRA_EVENTS_SQL = """
            WITH u AS (SELECT array_agg(id ORDER BY id) AS ids, count(*) AS n FROM users WHERE demo),
            days AS (
                SELECT g AS day_start, row_number() OVER (ORDER BY g) AS idx, count(*) OVER () AS total
                FROM generate_series(CAST(:eventsFrom AS timestamptz), CAST(:eventsTo AS timestamptz) - interval '1 day',
                                     interval '1 day') g
            ),
            imp AS (
                SELECT d.day_start, i, d.idx, d.total,
                       ('x' || substr(md5(d.day_start::text || i), 1, 8))::bit(32)::bigint AS h,
                       (SELECT array_agg(id ORDER BY id) FROM trips WHERE active AND service_date IS NULL) AS trip_ids
                FROM days d
                CROSS JOIN LATERAL generate_series(1, 560 + (extract(isodow FROM d.day_start)::int * 37)) i
            ),
            rows AS (
                SELECT CASE WHEN imp.h % 5 = 0 THEN NULL
                            ELSE u.ids[1 + floor(power(((imp.h / 5) % 10007)::numeric / 10007, 1.6) * u.n)::int] END
                           AS user_id,
                       imp.trip_ids[1 + (imp.h / 50000) % cardinality(imp.trip_ids)] AS trip_id,
                       imp.day_start + make_interval(secs => (imp.h % 86400)::int) AS at,
                       ((imp.h / 100) % 1000) < (85 + 60 * imp.idx / greatest(imp.total, 1)) AS clicked
                FROM imp CROSS JOIN u
            )
            INSERT INTO suggestion_events (user_id, trip_id, kind, created_at)
            SELECT user_id, trip_id, 'IMPRESSION', at FROM rows
            UNION ALL
            SELECT user_id, trip_id, 'CLICK', at + interval '20 seconds' FROM rows WHERE clicked
            """;
}
