package com.vego.backend.admin;

import com.vego.backend.admin.AdminDtos.Option;
import com.vego.backend.admin.AdminDtos.Options;
import com.vego.backend.admin.AdminDtos.RouteList;
import com.vego.backend.admin.AdminDtos.RouteRequest;
import com.vego.backend.admin.AdminDtos.RouteRow;
import com.vego.backend.admin.AdminDtos.Run;
import com.vego.backend.admin.AdminDtos.StationRef;
import com.vego.backend.admin.AdminDtos.StationRequest;
import com.vego.backend.admin.AdminDtos.TripRequest;
import com.vego.backend.admin.AdminDtos.TripTemplate;
import com.vego.backend.admin.ScheduleService.RunFilter;
import com.vego.backend.admin.ScheduleService.TripInfo;
import com.vego.backend.admin.ScheduleService.VehicleInfo;
import com.vego.backend.common.ApiException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/** Tuyến đường, chuyến cố định, bến xe và danh mục cho các ô chọn của web admin. */
@Service
public class RouteAdminService {

    private final AdminSupport db;
    private final ScheduleService schedule;

    public RouteAdminService(AdminSupport db, ScheduleService schedule) {
        this.db = db;
        this.schedule = schedule;
    }

    // ---------- Danh mục ----------

    @Transactional(readOnly = true)
    public Options options() {
        List<Option> operators = db.jdbc.query("SELECT id, code, name FROM operators ORDER BY name",
                (rs, i) -> new Option(rs.getInt("id"), rs.getString("code"), rs.getString("name")));
        List<StationRef> stations = db.jdbc.query("SELECT * FROM stations ORDER BY city, name",
                (rs, i) -> station(rs, ""));
        List<String> cities = db.jdbc.queryForList("""
                SELECT city FROM stations UNION SELECT origin_city FROM routes UNION SELECT destination_city FROM routes
                ORDER BY 1
                """, Map.of(), String.class);
        List<Option> routes = db.jdbc.query("""
                SELECT id, code, origin_city || ' → ' || destination_city AS name FROM routes ORDER BY code
                """, (rs, i) -> new Option(rs.getInt("id"), rs.getString("code"), rs.getString("name")));
        return new Options(operators, stations, cities, routes);
    }

    private static StationRef station(ResultSet rs, String prefix) throws SQLException {
        Object id = rs.getObject(prefix + "id");
        if (id == null) return null;
        return new StationRef(((Number) id).intValue(), rs.getString(prefix + "city"), rs.getString(prefix + "name"),
                rs.getString(prefix + "address"));
    }

    @Transactional
    public StationRef createStation(StationRequest req) {
        MapSqlParameterSource p = AdminSupport.params().addValue("city", req.city().trim())
                .addValue("name", req.name().trim()).addValue("address", req.address().trim());
        if (db.exists("SELECT 1 FROM stations WHERE lower(city) = lower(:city) AND lower(name) = lower(:name)", p)) {
            throw ApiException.conflict("STATION_EXISTS", "name", "Bến này đã có trong danh sách");
        }
        Integer id = db.jdbc.queryForObject(
                "INSERT INTO stations (city, name, address) VALUES (:city, :name, :address) RETURNING id", p,
                Integer.class);
        return new StationRef(id, req.city().trim(), req.name().trim(), req.address().trim());
    }

    // ---------- Tuyến ----------

    private static final String ROUTE_SQL = """
            SELECT r.id, r.code, r.origin_city, r.destination_city, r.duration_minutes, r.distance_km, r.active,
                   os.id AS os_id, os.city AS os_city, os.name AS os_name, os.address AS os_address,
                   ds.id AS ds_id, ds.city AS ds_city, ds.name AS ds_name, ds.address AS ds_address,
                   (SELECT count(*) FROM trips t WHERE t.route_id = r.id AND t.active AND t.service_date IS NULL)
                       AS templates
            FROM routes r
            LEFT JOIN stations os ON os.id = r.origin_station_id
            LEFT JOIN stations ds ON ds.id = r.destination_station_id
            """;

    private record RouteBase(int id, String code, String origin, String destination, Integer duration,
                             Integer distance, boolean active, StationRef os, StationRef ds, int templates) { }

    private List<RouteBase> routeBases(Integer id) {
        return db.jdbc.query(ROUTE_SQL + (id == null ? "" : " WHERE r.id = :id") + " ORDER BY r.code",
                AdminSupport.params().addValue("id", id), (rs, i) -> new RouteBase(rs.getInt("id"),
                        rs.getString("code"), rs.getString("origin_city"), rs.getString("destination_city"),
                        (Integer) rs.getObject("duration_minutes"), (Integer) rs.getObject("distance_km"),
                        rs.getBoolean("active"), station(rs, "os_"), station(rs, "ds_"), rs.getInt("templates")));
    }

    private static RouteRow row(RouteBase b, int runs) {
        return new RouteRow(b.id(), b.code(), b.origin(), b.destination(), b.origin() + " – " + b.destination(),
                b.duration(), b.distance(), b.active(), b.os(), b.ds(), b.templates(), runs);
    }

    @Transactional(readOnly = true)
    public RouteList routes(String q, String status, LocalDate date, Integer page, Integer size) {
        LocalDate d = date == null ? db.today() : date;
        Map<Integer, Long> runs = schedule.runs(d, d, RunFilter.NONE).stream()
                .collect(Collectors.groupingBy(Run::routeId, Collectors.counting()));
        List<RouteRow> all = routeBases(null).stream()
                .map(b -> row(b, runs.getOrDefault(b.id(), 0L).intValue())).toList();
        List<RouteRow> filtered = all.stream()
                .filter(r -> AdminSupport.matches(q, r.code(), r.originCity(), r.destinationCity(),
                        r.originStation() == null ? null : r.originStation().name(),
                        r.destinationStation() == null ? null : r.destinationStation().name()))
                .filter(r -> status == null || status.isBlank()
                        || ("ACTIVE".equalsIgnoreCase(status) == r.active()))
                .toList();
        int total = runs.values().stream().mapToInt(Long::intValue).sum();
        return new RouteList(all.size(), (int) all.stream().filter(RouteRow::active).count(), total, d,
                AdminSupport.page(filtered, page, size));
    }

    @Transactional(readOnly = true)
    public RouteRow route(int id) {
        RouteBase b = routeBases(id).stream().findFirst()
                .orElseThrow(() -> ApiException.notFound("ROUTE_NOT_FOUND", "Không tìm thấy tuyến"));
        LocalDate today = db.today();
        int runs = schedule.runs(today, today, new RunFilter(id, null, null, null)).size();
        return row(b, runs);
    }

    @Transactional
    public RouteRow createRoute(RouteRequest req) {
        String code = AdminSupport.trimToNull(req.code());
        code = code == null ? db.nextCode("routes", "T") : code.toUpperCase(Locale.ROOT);
        checkRoute(req, null);
        Integer id;
        try {
            id = db.jdbc.queryForObject("""
                    INSERT INTO routes (code, origin_city, destination_city, distance_km, duration_minutes,
                                        origin_station_id, destination_station_id, active)
                    VALUES (:code, :origin, :destination, :distance, :duration, :os, :ds, :active) RETURNING id
                    """, routeParams(req).addValue("code", code), Integer.class);
        } catch (DuplicateKeyException e) {
            throw duplicateRoute(e);
        }
        return route(id);
    }

    @Transactional
    public RouteRow updateRoute(int id, RouteRequest req) {
        RouteBase old = routeBases(id).stream().findFirst()
                .orElseThrow(() -> ApiException.notFound("ROUTE_NOT_FOUND", "Không tìm thấy tuyến"));
        checkRoute(req, id);
        boolean citiesChanged = !old.origin().equalsIgnoreCase(req.originCity().trim())
                || !old.destination().equalsIgnoreCase(req.destinationCity().trim());
        MapSqlParameterSource p = AdminSupport.params().addValue("id", id);
        if (citiesChanged && db.exists("SELECT 1 FROM bookings b JOIN trips t ON t.id = b.trip_id WHERE t.route_id = :id", p)) {
            throw ApiException.field("ROUTE_HAS_BOOKINGS", "originCity",
                    "Tuyến đã có vé bán, không đổi điểm đi / điểm đến được; hãy tạo tuyến mới");
        }
        String code = AdminSupport.trimToNull(req.code());
        code = code == null ? old.code() : code.toUpperCase(Locale.ROOT);
        try {
            db.jdbc.update("""
                    UPDATE routes SET code = :code, origin_city = :origin, destination_city = :destination,
                           distance_km = :distance, duration_minutes = :duration, origin_station_id = :os,
                           destination_station_id = :ds, active = :active
                    WHERE id = :id
                    """, routeParams(req).addValue("code", code).addValue("id", id));
        } catch (DuplicateKeyException e) {
            throw duplicateRoute(e);
        }
        return route(id);
    }

    @Transactional
    public void deleteRoute(int id) {
        RouteBase b = routeBases(id).stream().findFirst()
                .orElseThrow(() -> ApiException.notFound("ROUTE_NOT_FOUND", "Không tìm thấy tuyến"));
        MapSqlParameterSource p = AdminSupport.params().addValue("id", id);
        if (db.exists("SELECT 1 FROM trips WHERE route_id = :id", p)) {
            throw AdminSupport.conflict("ROUTE_HAS_TRIPS", "Tuyến " + b.code()
                    + " đang có chuyến; hãy chuyển trạng thái sang Tạm ngừng thay vì xóa");
        }
        db.jdbc.update("UPDATE promotions SET route_id = NULL WHERE route_id = :id", p);
        db.jdbc.update("DELETE FROM routes WHERE id = :id", p);
    }

    private void checkRoute(RouteRequest req, Integer id) {
        if (AdminSupport.fold(req.originCity()).equals(AdminSupport.fold(req.destinationCity()))) {
            throw ApiException.field("SAME_PLACE", "destinationCity", "Điểm đến phải khác điểm đi");
        }
        StationRef os = stationById(req.originStationId(), "originStationId");
        StationRef ds = stationById(req.destinationStationId(), "destinationStationId");
        if (!AdminSupport.fold(os.city()).equals(AdminSupport.fold(req.originCity()))) {
            throw ApiException.field("STATION_CITY", "originStationId",
                    os.name() + " ở " + os.city() + ", không thuộc điểm đi " + req.originCity().trim());
        }
        if (!AdminSupport.fold(ds.city()).equals(AdminSupport.fold(req.destinationCity()))) {
            throw ApiException.field("STATION_CITY", "destinationStationId",
                    ds.name() + " ở " + ds.city() + ", không thuộc điểm đến " + req.destinationCity().trim());
        }
        MapSqlParameterSource p = AdminSupport.params().addValue("o", req.originCity().trim())
                .addValue("d", req.destinationCity().trim()).addValue("id", id == null ? -1 : id);
        if (db.exists("""
                SELECT 1 FROM routes WHERE lower(origin_city) = lower(:o) AND lower(destination_city) = lower(:d)
                  AND id <> :id
                """, p)) {
            throw ApiException.conflict("ROUTE_EXISTS", "destinationCity", "Tuyến này đã có trong danh sách");
        }
    }

    private StationRef stationById(Integer id, String field) {
        return db.jdbc.query("SELECT * FROM stations WHERE id = :id", AdminSupport.params().addValue("id", id),
                (rs, i) -> station(rs, "")).stream().findFirst()
                .orElseThrow(() -> ApiException.field("STATION_NOT_FOUND", field, "Không tìm thấy bến xe"));
    }

    private MapSqlParameterSource routeParams(RouteRequest req) {
        return AdminSupport.params()
                .addValue("origin", req.originCity().trim())
                .addValue("destination", req.destinationCity().trim())
                .addValue("distance", req.distanceKm())
                .addValue("duration", req.durationMinutes())
                .addValue("os", req.originStationId())
                .addValue("ds", req.destinationStationId())
                .addValue("active", req.active() == null || req.active());
    }

    private static ApiException duplicateRoute(DuplicateKeyException e) {
        String msg = String.valueOf(e.getMostSpecificCause().getMessage());
        if (msg.contains("code")) return ApiException.conflict("CODE_EXISTS", "code", "Mã tuyến này đã được dùng");
        return ApiException.conflict("ROUTE_EXISTS", "destinationCity", "Tuyến này đã có trong danh sách");
    }

    // ---------- Chuyến trên tuyến ----------

    /** Các chuyến chạy của tuyến trong một ngày (bảng "Chuyến trên tuyến"). */
    @Transactional(readOnly = true)
    public List<Run> runs(int routeId, LocalDate date) {
        return schedule.runs(date, date, new RunFilter(routeId, null, null, null));
    }

    /** Mẫu chuyến cố định (và chuyến tăng cường sắp tới) của một tuyến. */
    @Transactional(readOnly = true)
    public List<TripTemplate> trips(Integer routeId) {
        MapSqlParameterSource p = AdminSupport.params().addValue("routeId", routeId)
                .addValue("today", Date.valueOf(db.today()));
        String where = " WHERE (t.service_date IS NULL OR t.service_date >= :today)"
                + (routeId == null ? "" : " AND t.route_id = :routeId");
        return db.jdbc.query(ScheduleService.TEMPLATE_SQL + where + " ORDER BY t.route_id, t.depart_time, t.id", p,
                (rs, i) -> ScheduleService.template(rs));
    }

    @Transactional
    public TripTemplate createTrip(TripRequest req) {
        RouteRow route = route(req.routeId());
        VehicleInfo v = schedule.vehicleInfo(req.vehicleId())
                .orElseThrow(() -> ApiException.field("VEHICLE_NOT_FOUND", "vehicleId", "Không tìm thấy xe"));
        LocalTime time = LocalTime.parse(req.departTime());
        Integer pickup = req.pickupStationId() != null ? req.pickupStationId()
                : route.originStation() == null ? null : route.originStation().id();
        Integer dropoff = req.dropoffStationId() != null ? req.dropoffStationId()
                : route.destinationStation() == null ? null : route.destinationStation().id();
        if (pickup == null || dropoff == null) {
            throw ApiException.field("STATION_REQUIRED", "pickupStationId", "Tuyến chưa có bến đón / trả");
        }
        Integer duration = req.durationMinutes() != null ? req.durationMinutes() : route.durationMinutes();
        if (duration == null) throw ApiException.field("DURATION_REQUIRED", "durationMinutes", "Vui lòng nhập thời lượng");
        if (db.exists("""
                SELECT 1 FROM trips WHERE route_id = :routeId AND depart_time = :time AND operator_id = :op
                  AND active AND service_date IS NULL
                """, AdminSupport.params().addValue("routeId", req.routeId()).addValue("time", time)
                .addValue("op", v.operatorId()))) {
            throw ApiException.conflict("TRIP_EXISTS", "departTime",
                    "Nhà xe " + v.operator() + " đã có chuyến " + req.departTime() + " trên tuyến này");
        }
        String opCode = db.jdbc.queryForObject("SELECT code FROM operators WHERE id = :id",
                AdminSupport.params().addValue("id", v.operatorId()), String.class);
        String id = schedule.newTripId(opCode, time, null);
        db.jdbc.update("""
                INSERT INTO trips (id, route_id, operator_id, bus_name, bus_type, rating, depart_time, duration_minutes,
                                   price, total_seats, seats_left, pickup_station_id, dropoff_station_id, active,
                                   vehicle_id)
                VALUES (:id, :routeId, :op, :busName, :busType, 4.5, :time, :duration, :price, :seats, :seats,
                        :pickup, :dropoff, :active, :vehicleId)
                """, AdminSupport.params().addValue("id", id).addValue("routeId", req.routeId())
                .addValue("op", v.operatorId()).addValue("busName", AdminSupport.typeLabel(v.busType(), v.totalSeats()))
                .addValue("busType", v.busType()).addValue("time", time).addValue("duration", duration)
                .addValue("price", req.price()).addValue("seats", v.totalSeats()).addValue("pickup", pickup)
                .addValue("dropoff", dropoff).addValue("active", req.active() == null || req.active())
                .addValue("vehicleId", v.id()));
        return schedule.template(id).orElseThrow();
    }

    @Transactional
    public TripTemplate updateTrip(String tripId, TripRequest req) {
        TripInfo old = schedule.tripInfo(tripId)
                .orElseThrow(() -> ApiException.notFound("TRIP_NOT_FOUND", "Không tìm thấy chuyến"));
        VehicleInfo v = schedule.vehicleInfo(req.vehicleId())
                .orElseThrow(() -> ApiException.field("VEHICLE_NOT_FOUND", "vehicleId", "Không tìm thấy xe"));
        LocalTime time = LocalTime.parse(req.departTime());
        MapSqlParameterSource p = AdminSupport.params().addValue("id", old.id())
                .addValue("today", Date.valueOf(db.today()));
        boolean hasFutureBookings = db.exists("""
                SELECT 1 FROM bookings WHERE trip_id = :id AND travel_date >= :today AND status IN ('HOLDING', 'PAID')
                """, p);
        boolean layoutChanged = !v.busType().equals(old.busType()) || v.totalSeats() != old.totalSeats();
        if (hasFutureBookings && layoutChanged) {
            throw ApiException.field("TRIP_HAS_BOOKINGS", "vehicleId", "Chuyến đã có khách đặt chỗ, chỉ đổi được sang xe "
                    + AdminSupport.typeLabel(old.busType(), old.totalSeats()).toLowerCase(Locale.ROOT));
        }
        if (hasFutureBookings && !time.equals(old.departTime())) {
            throw ApiException.field("TRIP_HAS_BOOKINGS", "departTime",
                    "Chuyến đã có khách đặt chỗ, không đổi giờ khởi hành được");
        }
        if (v.operatorId() != old.operatorId()) {
            throw ApiException.field("OPERATOR_MISMATCH", "vehicleId", "Xe " + v.code() + " thuộc nhà xe khác");
        }
        if ("MAINTENANCE".equals(v.status())) {
            throw ApiException.field("VEHICLE_MAINTENANCE", "vehicleId", "Xe " + v.code() + " đang bảo trì");
        }
        int duration = req.durationMinutes() != null ? req.durationMinutes() : old.durationMinutes();
        boolean active = req.active() == null || req.active();
        p.addValue("time", time).addValue("duration", duration).addValue("price", req.price())
                .addValue("vehicleId", v.id()).addValue("busType", v.busType()).addValue("seats", v.totalSeats())
                .addValue("busName", AdminSupport.typeLabel(v.busType(), v.totalSeats())).addValue("active", active)
                .addValue("pickup", req.pickupStationId()).addValue("dropoff", req.dropoffStationId());
        db.jdbc.update("""
                UPDATE trips SET depart_time = :time, duration_minutes = :duration, price = :price,
                       vehicle_id = :vehicleId, bus_type = :busType, bus_name = :busName,
                       seats_left = CASE WHEN total_seats = :seats THEN seats_left ELSE :seats END,
                       total_seats = :seats, active = :active,
                       pickup_station_id = coalesce(:pickup, pickup_station_id),
                       dropoff_station_id = coalesce(:dropoff, dropoff_station_id)
                WHERE id = :id
                """, p);
        return schedule.template(old.id()).orElseThrow();
    }

    /** Xóa chuyến: chưa từng bán vé thì xóa hẳn, đã có vé thì ngừng chạy (giữ lịch sử). */
    @Transactional
    public boolean deleteTrip(String tripId) {
        TripInfo old = schedule.tripInfo(tripId)
                .orElseThrow(() -> ApiException.notFound("TRIP_NOT_FOUND", "Không tìm thấy chuyến"));
        MapSqlParameterSource p = AdminSupport.params().addValue("id", old.id())
                .addValue("today", Date.valueOf(db.today()));
        if (db.exists("""
                SELECT 1 FROM bookings WHERE trip_id = :id AND travel_date >= :today AND status IN ('HOLDING', 'PAID')
                """, p)) {
            throw AdminSupport.conflict("TRIP_HAS_BOOKINGS",
                    "Chuyến " + old.id() + " còn vé chưa đi; hãy hủy / chuyển khách trước khi ngừng chạy");
        }
        if (db.exists("SELECT 1 FROM bookings WHERE trip_id = :id", p)
                || db.exists("SELECT 1 FROM reviews WHERE trip_id = :id", p)) {
            db.jdbc.update("UPDATE trips SET active = FALSE WHERE id = :id", p);
            db.jdbc.update("DELETE FROM trip_assignments WHERE trip_id = :id AND travel_date >= :today", p);
            return false;
        }
        db.jdbc.update("DELETE FROM trip_assignments WHERE trip_id = :id", p);
        db.jdbc.update("DELETE FROM suggestion_events WHERE trip_id = :id", p);
        db.jdbc.update("DELETE FROM trips WHERE id = :id", p);
        return true;
    }
}
