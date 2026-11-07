package com.vego.backend.admin;

import com.vego.backend.admin.AdminDtos.AssignRequest;
import com.vego.backend.admin.AdminDtos.DriverRef;
import com.vego.backend.admin.AdminDtos.Run;
import com.vego.backend.admin.AdminDtos.ScheduleRequest;
import com.vego.backend.admin.AdminDtos.SchedulePreview;
import com.vego.backend.admin.AdminDtos.StationRef;
import com.vego.backend.admin.AdminDtos.TripTemplate;
import com.vego.backend.admin.AdminDtos.VehicleRef;
import com.vego.backend.common.ApiException;
import com.vego.backend.home.Trip;
import com.vego.backend.home.TripRepository;
import com.vego.backend.trip.SeatInventory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Lịch chạy: chuyến cố định chạy mỗi ngày (trips.service_date = null) và chuyến tăng cường một ngày;
 * phân công xe + tài xế cho từng chuyến trong từng ngày (trip_assignments, không có thì dùng xe mặc định).
 */
@Service
public class ScheduleService {

    /** Khoảng nghỉ tối thiểu giữa hai chuyến của cùng một xe / tài xế. */
    static final Duration TURNAROUND = Duration.ofMinutes(30);
    private static final DateTimeFormatter HM = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DDMM = DateTimeFormatter.ofPattern("ddMM");

    /** Bộ lọc chuyến chạy (null = không lọc). */
    public record RunFilter(Integer routeId, Integer vehicleId, Integer driverId, String tripId) {
        static final RunFilter NONE = new RunFilter(null, null, null, null);
    }

    /** Thông tin một chuyến (mẫu lịch) dùng khi kiểm tra phân công. */
    record TripInfo(String id, int routeId, int operatorId, String operatorCode, String busType, int totalSeats,
                    LocalTime departTime, int durationMinutes, int price, Integer vehicleId, LocalDate serviceDate,
                    boolean active) {
        boolean runsOn(LocalDate d) { return serviceDate == null || serviceDate.equals(d); }
    }

    record VehicleInfo(int id, String code, String plate, int operatorId, String operator, String busType,
                       int totalSeats, String status) { }

    record DriverInfo(int id, String code, String fullName, String phone, String licenseClass,
                      LocalDate licenseExpiry, String status) { }

    private static final String RUNS_SQL = """
            SELECT t.id AS trip_id, t.route_id, r.code AS route_code, r.origin_city, r.destination_city,
                   d::date AS travel_date, t.depart_time, t.duration_minutes, t.price, t.total_seats, t.bus_type,
                   t.service_date, o.name AS operator_name,
                   v.id AS vehicle_id, v.code AS vehicle_code, v.plate, v.status AS vehicle_status,
                   dr.id AS driver_id, dr.code AS driver_code, dr.full_name AS driver_name, dr.phone AS driver_phone,
                   a.note,
                   (SELECT count(*) FROM bookings b
                     WHERE b.trip_id = t.id AND b.travel_date = d::date AND b.status = 'PAID') AS paid
            FROM trips t
            JOIN routes r ON r.id = t.route_id
            JOIN operators o ON o.id = t.operator_id
            CROSS JOIN generate_series(CAST(:from AS date), CAST(:to AS date), interval '1 day') d
            LEFT JOIN trip_assignments a ON a.trip_id = t.id AND a.travel_date = d::date
            LEFT JOIN vehicles v ON v.id = coalesce(a.vehicle_id, t.vehicle_id)
            LEFT JOIN drivers dr ON dr.id = a.driver_id
            WHERE t.active AND (t.service_date IS NULL OR t.service_date = d::date)
            """;

    private final AdminSupport db;
    private final TripRepository trips;
    private final SeatInventory seats;

    public ScheduleService(AdminSupport db, TripRepository trips, SeatInventory seats) {
        this.db = db;
        this.trips = trips;
        this.seats = seats;
    }

    // ---------- Đọc lịch ----------

    /** Các chuyến chạy trong [from, to], sắp theo giờ khởi hành. */
    @Transactional(readOnly = true)
    public List<Run> runs(LocalDate from, LocalDate to, RunFilter filter) {
        RunFilter f = filter == null ? RunFilter.NONE : filter;
        StringBuilder sql = new StringBuilder(RUNS_SQL);
        MapSqlParameterSource p = AdminSupport.params()
                .addValue("from", Date.valueOf(from)).addValue("to", Date.valueOf(to));
        if (f.routeId() != null) {
            sql.append(" AND t.route_id = :routeId");
            p.addValue("routeId", f.routeId());
        }
        if (f.vehicleId() != null) {
            sql.append(" AND coalesce(a.vehicle_id, t.vehicle_id) = :vehicleId");
            p.addValue("vehicleId", f.vehicleId());
        }
        if (f.driverId() != null) {
            sql.append(" AND a.driver_id = :driverId");
            p.addValue("driverId", f.driverId());
        }
        if (f.tripId() != null) {
            sql.append(" AND upper(t.id) = upper(:tripId)");
            p.addValue("tripId", f.tripId());
        }
        List<RawRun> raw = db.jdbc.query(sql.toString(), p, (rs, i) -> RawRun.of(rs));
        return toRuns(raw);
    }

    /** Một chuyến chạy cụ thể (mã chuyến + ngày). */
    @Transactional(readOnly = true)
    public Optional<Run> run(String tripId, LocalDate date) {
        return runs(date, date, new RunFilter(null, null, null, tripId)).stream().findFirst();
    }

    private record RawRun(String tripId, int routeId, String routeCode, String origin, String destination,
                          LocalDate date, LocalTime departTime, int duration, int price, int totalSeats,
                          String busType, LocalDate serviceDate, String operator, VehicleRef vehicle,
                          DriverRef driver, String note, long paid) {
        static RawRun of(ResultSet rs) throws SQLException {
            Integer vid = (Integer) rs.getObject("vehicle_id");
            Integer did = (Integer) rs.getObject("driver_id");
            Date sd = rs.getDate("service_date");
            return new RawRun(rs.getString("trip_id"), rs.getInt("route_id"), rs.getString("route_code"),
                    rs.getString("origin_city"), rs.getString("destination_city"),
                    rs.getDate("travel_date").toLocalDate(), rs.getObject("depart_time", LocalTime.class),
                    rs.getInt("duration_minutes"), rs.getInt("price"), rs.getInt("total_seats"),
                    rs.getString("bus_type"), sd == null ? null : sd.toLocalDate(), rs.getString("operator_name"),
                    vid == null ? null : new VehicleRef(vid, rs.getString("vehicle_code"), rs.getString("plate"),
                            rs.getString("vehicle_status")),
                    did == null ? null : new DriverRef(did, rs.getString("driver_code"), rs.getString("driver_name"),
                            rs.getString("driver_phone")),
                    rs.getString("note"), rs.getLong("paid"));
        }
    }

    private List<Run> toRuns(List<RawRun> raw) {
        if (raw.isEmpty()) return List.of();
        LocalDate today = db.today();
        Instant now = db.now();
        // Ghế không còn bán được (giống app) cho các ngày từ hôm nay trở đi
        Map<String, Trip> entities = new HashMap<>();
        trips.findAllById(raw.stream().filter(r -> !r.date().isBefore(today)).map(RawRun::tripId)
                .collect(Collectors.toSet())).forEach(t -> entities.put(t.getId(), t));
        Map<LocalDate, Map<String, Integer>> leftByDate = new HashMap<>();
        raw.stream().filter(r -> !r.date().isBefore(today))
                .collect(Collectors.groupingBy(RawRun::date, Collectors.mapping(RawRun::tripId, Collectors.toSet())))
                .forEach((date, ids) -> leftByDate.put(date, seats.seatsLeft(
                        ids.stream().map(entities::get).filter(java.util.Objects::nonNull).toList(), date)));

        List<Run> out = new ArrayList<>(raw.size());
        for (RawRun r : raw) {
            LocalDateTime dep = r.date().atTime(r.departTime());
            LocalDateTime arr = dep.plusMinutes(r.duration());
            Instant departAt = dep.atZone(db.zone).toInstant();
            Instant arriveAt = arr.atZone(db.zone).toInstant();
            int sold;
            if (r.date().isBefore(today)) {
                sold = (int) Math.min(r.paid(), r.totalSeats());
            } else {
                Integer left = leftByDate.getOrDefault(r.date(), Map.of()).get(r.tripId());
                sold = left == null ? (int) r.paid() : r.totalSeats() - left;
            }
            String state = now.isBefore(departAt) ? "SCHEDULED" : now.isBefore(arriveAt) ? "RUNNING" : "DONE";
            out.add(new Run(code(r.tripId(), r.date()), r.tripId(), r.date(), r.routeId(), r.routeCode(),
                    r.origin() + " → " + r.destination(), r.operator(), r.busType(), departAt, arriveAt,
                    dep.format(HM), arr.format(HM), (int) (arr.toLocalDate().toEpochDay() - r.date().toEpochDay()),
                    r.price(), r.totalSeats(), sold, r.serviceDate() != null, r.vehicle(), r.driver(), r.note(),
                    state));
        }
        out.sort(Comparator.comparing(Run::departAt).thenComparing(Run::tripId));
        return out;
    }

    /** Mã chuyến chạy theo ngày, vd PT2200-1811. */
    static String code(String tripId, LocalDate date) {
        return tripId + "-" + date.format(DDMM);
    }

    // ---------- Thông tin chuyến / xe / tài xế ----------

    Optional<TripInfo> tripInfo(String tripId) {
        return db.jdbc.query("""
                SELECT t.id, t.route_id, t.operator_id, o.code AS operator_code, t.bus_type, t.total_seats,
                       t.depart_time, t.duration_minutes, t.price, t.vehicle_id, t.service_date, t.active
                FROM trips t JOIN operators o ON o.id = t.operator_id WHERE upper(t.id) = upper(:id)
                """, AdminSupport.params().addValue("id", tripId.trim()), (rs, i) -> tripInfo(rs))
                .stream().findFirst();
    }

    private static TripInfo tripInfo(ResultSet rs) throws SQLException {
        Date sd = rs.getDate("service_date");
        return new TripInfo(rs.getString("id"), rs.getInt("route_id"), rs.getInt("operator_id"),
                rs.getString("operator_code"), rs.getString("bus_type"), rs.getInt("total_seats"),
                rs.getObject("depart_time", LocalTime.class), rs.getInt("duration_minutes"), rs.getInt("price"),
                (Integer) rs.getObject("vehicle_id"), sd == null ? null : sd.toLocalDate(), rs.getBoolean("active"));
    }

    Optional<VehicleInfo> vehicleInfo(int id) {
        return db.jdbc.query("""
                SELECT v.id, v.code, v.plate, v.operator_id, o.name AS operator_name, v.bus_type, v.total_seats,
                       v.status
                FROM vehicles v JOIN operators o ON o.id = v.operator_id WHERE v.id = :id
                """, AdminSupport.params().addValue("id", id), (rs, i) -> new VehicleInfo(rs.getInt("id"),
                rs.getString("code"), rs.getString("plate"), rs.getInt("operator_id"), rs.getString("operator_name"),
                rs.getString("bus_type"), rs.getInt("total_seats"), rs.getString("status"))).stream().findFirst();
    }

    Optional<DriverInfo> driverInfo(int id) {
        return db.jdbc.query("""
                SELECT id, code, full_name, phone, license_class, license_expiry, status FROM drivers WHERE id = :id
                """, AdminSupport.params().addValue("id", id), (rs, i) -> new DriverInfo(rs.getInt("id"),
                rs.getString("code"), rs.getString("full_name"), rs.getString("phone"), rs.getString("license_class"),
                rs.getDate("license_expiry").toLocalDate(), rs.getString("status"))).stream().findFirst();
    }

    private VehicleInfo requireVehicle(Integer id) {
        if (id == null) throw ApiException.field("VEHICLE_REQUIRED", "vehicleId", "Vui lòng chọn xe");
        return vehicleInfo(id).orElseThrow(() -> ApiException.notFound("VEHICLE_NOT_FOUND", "Không tìm thấy xe"));
    }

    private DriverInfo requireDriver(int id) {
        return driverInfo(id).orElseThrow(() -> ApiException.notFound("DRIVER_NOT_FOUND", "Không tìm thấy tài xế"));
    }

    // ---------- Kiểm tra phân công ----------

    /**
     * Kiểm tra xe / tài xế cho một chuyến chạy. Lỗi: xe bảo trì, sai loại / số chỗ / nhà xe, tài xế tạm nghỉ,
     * GPLX hết hạn hoặc không đủ hạng, trùng lịch với chuyến khác. Cảnh báo: GPLX sắp hết hạn.
     */
    void check(TripInfo trip, String busType, int totalSeats, int operatorId, LocalDate date, Instant departAt,
               Instant arriveAt, VehicleInfo v, DriverInfo d, List<String> errors, List<String> warnings) {
        if (v != null) {
            if ("MAINTENANCE".equals(v.status())) errors.add("Xe " + v.code() + " đang bảo trì");
            if (!v.busType().equals(busType) || v.totalSeats() != totalSeats) {
                errors.add("Xe " + v.code() + " là " + AdminSupport.typeLabel(v.busType(), v.totalSeats())
                        + ", chuyến cần xe " + AdminSupport.typeLabel(busType, totalSeats).toLowerCase(Locale.ROOT)
                        + " (sơ đồ ghế đã mở bán)");
            }
            if (v.operatorId() != operatorId) errors.add("Xe " + v.code() + " thuộc nhà xe " + v.operator());
            for (Run r : overlapping(departAt, arriveAt, new RunFilter(null, v.id(), null, null), trip, date)) {
                errors.add("Xe " + v.code() + " trùng lịch chuyến " + r.code() + " (" + r.departTime() + " → "
                        + r.arriveTime() + ")");
            }
        }
        if (d != null) {
            if ("OFF".equals(d.status())) errors.add("Tài xế " + d.fullName() + " đang tạm nghỉ");
            if (d.licenseExpiry().isBefore(date)) {
                errors.add("GPLX của " + d.fullName() + " hết hạn ngày " + vn(d.licenseExpiry()));
            } else if (d.licenseExpiry().isBefore(date.plusDays(60))) {
                warnings.add("GPLX của " + d.fullName() + " sẽ hết hạn ngày " + vn(d.licenseExpiry()));
            }
            if (AdminSupport.maxSeats(d.licenseClass()) < totalSeats) {
                errors.add("GPLX hạng " + d.licenseClass() + " không được lái xe " + totalSeats + " chỗ");
            }
            for (Run r : overlapping(departAt, arriveAt, new RunFilter(null, null, d.id(), null), trip, date)) {
                errors.add("Tài xế " + d.fullName() + " trùng lịch chuyến " + r.code() + " (" + r.departTime()
                        + " → " + r.arriveTime() + ")");
            }
        }
    }

    private List<Run> overlapping(Instant dep, Instant arr, RunFilter f, TripInfo self, LocalDate selfDate) {
        LocalDate day = dep.atZone(db.zone).toLocalDate();
        return runs(day.minusDays(2), day.plusDays(2), f).stream()
                .filter(r -> self == null || !(r.tripId().equals(self.id()) && r.date().equals(selfDate)))
                .filter(r -> r.departAt().isBefore(arr.plus(TURNAROUND)) && r.arriveAt().plus(TURNAROUND).isAfter(dep))
                .toList();
    }

    private static String vn(LocalDate d) {
        return d.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
    }

    // ---------- Lập lịch chuyến ----------

    /** Chuyến (cố định hoặc tăng cường) của tuyến chạy đúng ngày + giờ này. */
    private Optional<TripInfo> findTrip(int routeId, LocalDate date, LocalTime time) {
        return db.jdbc.query("""
                SELECT t.id, t.route_id, t.operator_id, o.code AS operator_code, t.bus_type, t.total_seats,
                       t.depart_time, t.duration_minutes, t.price, t.vehicle_id, t.service_date, t.active
                FROM trips t JOIN operators o ON o.id = t.operator_id
                WHERE t.route_id = :routeId AND t.active AND t.depart_time = :time
                  AND (t.service_date IS NULL OR t.service_date = :date)
                ORDER BY t.service_date NULLS FIRST, t.id
                """, AdminSupport.params().addValue("routeId", routeId).addValue("time", time)
                .addValue("date", Date.valueOf(date)), (rs, i) -> tripInfo(rs)).stream().findFirst();
    }

    private record RouteInfo(int id, String code, Integer duration, Integer originStation, Integer destStation,
                             boolean active) { }

    private RouteInfo requireRoute(Integer id) {
        if (id == null) throw ApiException.field("ROUTE_REQUIRED", "routeId", "Vui lòng chọn tuyến");
        return db.jdbc.query("""
                SELECT id, code, duration_minutes, origin_station_id, destination_station_id, active
                FROM routes WHERE id = :id
                """, AdminSupport.params().addValue("id", id), (rs, i) -> new RouteInfo(rs.getInt("id"),
                rs.getString("code"), (Integer) rs.getObject("duration_minutes"),
                (Integer) rs.getObject("origin_station_id"), (Integer) rs.getObject("destination_station_id"),
                rs.getBoolean("active"))).stream().findFirst()
                .orElseThrow(() -> ApiException.notFound("ROUTE_NOT_FOUND", "Không tìm thấy tuyến"));
    }

    @Transactional(readOnly = true)
    public SchedulePreview preview(ScheduleRequest req) {
        return plan(req).preview();
    }

    private record Plan(SchedulePreview preview, RouteInfo route, TripInfo existing, VehicleInfo vehicle,
                        DriverInfo driver, LocalTime time, int duration, int price) { }

    private Plan plan(ScheduleRequest req) {
        RouteInfo route = requireRoute(req.routeId());
        VehicleInfo v = requireVehicle(req.vehicleId());
        DriverInfo d = req.driverId() == null ? null : requireDriver(req.driverId());
        LocalTime time = LocalTime.parse(req.departTime());
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        if (!route.active()) errors.add("Tuyến " + route.code() + " đang tạm ngừng");

        TripInfo existing = findTrip(route.id(), req.date(), time).orElse(null);
        int duration;
        int price;
        String busType;
        int totalSeats;
        int operatorId;
        if (existing != null) {
            duration = existing.durationMinutes();
            price = existing.price();
            busType = existing.busType();
            totalSeats = existing.totalSeats();
            operatorId = existing.operatorId();
            warnings.add("Đã có chuyến " + existing.id() + " lúc " + req.departTime()
                    + ": lưu sẽ phân công xe và tài xế cho chuyến này");
            if (req.price() != null && req.price() != existing.price()) {
                warnings.add("Giá vé giữ theo chuyến " + existing.id() + ": " + money(existing.price()));
            }
        } else {
            duration = req.durationMinutes() != null ? req.durationMinutes()
                    : route.duration() != null ? route.duration() : 0;
            if (duration <= 0) errors.add("Vui lòng nhập thời lượng chuyến");
            if (req.price() == null) errors.add("Vui lòng nhập giá vé");
            price = req.price() == null ? 0 : req.price();
            busType = v.busType();
            totalSeats = v.totalSeats();
            operatorId = v.operatorId();
            if (route.originStation() == null || route.destStation() == null) {
                errors.add("Tuyến " + route.code() + " chưa có bến đón / trả, hãy cập nhật tuyến trước");
            }
        }
        Instant departAt = req.date().atTime(time).atZone(db.zone).toInstant();
        Instant arriveAt = departAt.plus(Duration.ofMinutes(Math.max(duration, 0)));
        if (!departAt.isAfter(db.now())) errors.add("Giờ khởi hành đã qua");
        if (req.date().isAfter(db.today().plusDays(60))) errors.add("Chỉ lập lịch trước tối đa 60 ngày");
        check(existing, busType, totalSeats, operatorId, req.date(), departAt, arriveAt, v, d, errors, warnings);

        LocalDate day = req.date();
        List<Run> vehicleRuns = runs(day.minusDays(1), day.plusDays(1), new RunFilter(null, v.id(), null, null));
        TripTemplate tpl = existing == null ? null : template(existing.id()).orElse(null);
        return new Plan(new SchedulePreview(tpl, departAt, arriveAt, price, totalSeats, errors, warnings,
                vehicleRuns), route, existing, v, d, time, duration, price);
    }

    /** Lưu lịch: phân công cho chuyến có sẵn, hoặc tạo chuyến tăng cường chỉ chạy ngày đó. */
    @Transactional
    public Run create(ScheduleRequest req) {
        Plan plan = plan(req);
        if (!plan.preview().errors().isEmpty()) throw AdminSupport.errors(plan.preview().errors());
        String tripId;
        if (plan.existing() != null) {
            tripId = plan.existing().id();
        } else {
            VehicleInfo v = plan.vehicle();
            tripId = newTripId(opCode(v.operatorId()), plan.time(), req.date());
            db.jdbc.update("""
                    INSERT INTO trips (id, route_id, operator_id, bus_name, bus_type, rating, depart_time,
                                       duration_minutes, price, total_seats, seats_left, pickup_station_id,
                                       dropoff_station_id, active, service_date, vehicle_id)
                    VALUES (:id, :routeId, :operatorId, :busName, :busType, 4.5, :time, :duration, :price, :seats,
                            :seats, :pickup, :dropoff, TRUE, :date, :vehicleId)
                    """, AdminSupport.params().addValue("id", tripId).addValue("routeId", plan.route().id())
                    .addValue("operatorId", v.operatorId())
                    .addValue("busName", AdminSupport.typeLabel(v.busType(), v.totalSeats()))
                    .addValue("busType", v.busType()).addValue("time", plan.time())
                    .addValue("duration", plan.duration()).addValue("price", plan.price())
                    .addValue("seats", v.totalSeats()).addValue("pickup", plan.route().originStation())
                    .addValue("dropoff", plan.route().destStation()).addValue("date", Date.valueOf(req.date()))
                    .addValue("vehicleId", v.id()));
        }
        upsert(tripId, req.date(), plan.vehicle().id(), plan.driver() == null ? null : plan.driver().id(),
                AdminSupport.trimToNull(req.note()), plan.driver() == null);
        return run(tripId, req.date()).orElseThrow();
    }

    /** Phân công xe / tài xế cho một chuyến chạy (màn Tài xế, Tuyến & chuyến). */
    @Transactional
    public Run assign(AssignRequest req) {
        TripInfo trip = tripInfo(req.tripId())
                .orElseThrow(() -> ApiException.notFound("TRIP_NOT_FOUND", "Không tìm thấy chuyến"));
        if (!trip.active() || !trip.runsOn(req.date())) {
            throw AdminSupport.badRequest("TRIP_NOT_RUNNING", "Chuyến " + trip.id() + " không chạy ngày này");
        }
        Optional<Run> current = run(trip.id(), req.date());
        Integer vehicleId = req.vehicleId() != null ? req.vehicleId()
                : current.map(Run::vehicle).map(VehicleRef::id).orElse(trip.vehicleId());
        VehicleInfo v = vehicleId == null ? null : requireVehicle(vehicleId);
        DriverInfo d = req.driverId() == null ? null : requireDriver(req.driverId());
        Instant departAt = req.date().atTime(trip.departTime()).atZone(db.zone).toInstant();
        Instant arriveAt = departAt.plus(Duration.ofMinutes(trip.durationMinutes()));
        if (!arriveAt.isAfter(db.now())) {
            throw AdminSupport.badRequest("TRIP_FINISHED", "Chuyến này đã kết thúc, không đổi phân công được");
        }
        List<String> errors = new ArrayList<>();
        check(trip, trip.busType(), trip.totalSeats(), trip.operatorId(), req.date(), departAt, arriveAt, v, d,
                errors, new ArrayList<>());
        if (!errors.isEmpty()) throw AdminSupport.errors(errors);
        upsert(trip.id(), req.date(), vehicleId, req.driverId(), AdminSupport.trimToNull(req.note()), false);
        return run(trip.id(), req.date()).orElseThrow();
    }

    /** Bỏ tài xế khỏi chuyến (nút "Hủy phân công chuyến"). */
    @Transactional
    public void unassignDriver(String tripId, LocalDate date) {
        int n = db.jdbc.update("""
                UPDATE trip_assignments SET driver_id = NULL, updated_at = now()
                WHERE upper(trip_id) = upper(:tripId) AND travel_date = :date
                """, AdminSupport.params().addValue("tripId", tripId).addValue("date", Date.valueOf(date)));
        if (n == 0) throw ApiException.notFound("ASSIGNMENT_NOT_FOUND", "Chuyến này chưa có phân công");
    }

    /**
     * Xóa lịch của một chuyến chạy: chuyến tăng cường chưa bán vé thì xóa hẳn,
     * chuyến cố định thì bỏ phân công (quay về xe mặc định).
     */
    @Transactional
    public void delete(String tripId, LocalDate date) {
        TripInfo trip = tripInfo(tripId)
                .orElseThrow(() -> ApiException.notFound("TRIP_NOT_FOUND", "Không tìm thấy chuyến"));
        MapSqlParameterSource p = AdminSupport.params().addValue("tripId", trip.id())
                .addValue("date", Date.valueOf(date));
        if (trip.serviceDate() != null) {
            if (db.exists("SELECT 1 FROM bookings WHERE trip_id = :tripId", p)) {
                throw AdminSupport.conflict("TRIP_HAS_BOOKINGS",
                        "Chuyến " + trip.id() + " đã có khách đặt vé, không xóa được");
            }
            db.jdbc.update("DELETE FROM trip_assignments WHERE trip_id = :tripId", p);
            db.jdbc.update("DELETE FROM suggestion_events WHERE trip_id = :tripId", p);
            db.jdbc.update("DELETE FROM trips WHERE id = :tripId", p);
            return;
        }
        int n = db.jdbc.update("DELETE FROM trip_assignments WHERE trip_id = :tripId AND travel_date = :date", p);
        if (n == 0) throw ApiException.notFound("ASSIGNMENT_NOT_FOUND", "Chuyến này chưa có phân công");
    }

    private void upsert(String tripId, LocalDate date, Integer vehicleId, Integer driverId, String note,
                        boolean keepDriver) {
        db.jdbc.update("""
                INSERT INTO trip_assignments (trip_id, travel_date, vehicle_id, driver_id, note)
                VALUES (:tripId, :date, :vehicleId, :driverId, :note)
                ON CONFLICT (trip_id, travel_date) DO UPDATE SET
                    vehicle_id = EXCLUDED.vehicle_id,
                    driver_id = CASE WHEN :keepDriver THEN coalesce(EXCLUDED.driver_id, trip_assignments.driver_id)
                                     ELSE EXCLUDED.driver_id END,
                    note = coalesce(EXCLUDED.note, trip_assignments.note),
                    updated_at = now()
                """, AdminSupport.params().addValue("tripId", tripId).addValue("date", Date.valueOf(date))
                .addValue("vehicleId", vehicleId).addValue("driverId", driverId).addValue("note", note)
                .addValue("keepDriver", keepDriver));
    }

    private String opCode(int operatorId) {
        return db.jdbc.queryForObject("SELECT code FROM operators WHERE id = :id",
                AdminSupport.params().addValue("id", operatorId), String.class);
    }

    /** Mã chuyến mới: nhà xe + giờ (PT2200), chuyến tăng cường thêm ngày (PT2200-1811), trùng thì thêm chữ cái. */
    String newTripId(String operatorCode, LocalTime time, LocalDate serviceDate) {
        String base = operatorCode.toUpperCase(Locale.ROOT) + time.format(DateTimeFormatter.ofPattern("HHmm"))
                + (serviceDate == null ? "" : "-" + serviceDate.format(DDMM));
        String id = base;
        for (char c = 'B'; db.exists("SELECT 1 FROM trips WHERE upper(id) = upper(:id)",
                AdminSupport.params().addValue("id", id)); c++) {
            if (c > 'Z') throw AdminSupport.conflict("TRIP_ID_EXHAUSTED", "Không tạo được mã chuyến mới");
            id = base + c;
        }
        return id;
    }

    // ---------- Mẫu chuyến (dùng chung với màn Tuyến & chuyến) ----------

    static final String TEMPLATE_SQL = """
            SELECT t.id, t.route_id, t.operator_id, o.name AS operator_name, t.bus_type, t.bus_name, t.depart_time,
                   t.duration_minutes, t.price, t.total_seats, t.service_date, t.active,
                   v.id AS vehicle_id, v.code AS vehicle_code, v.plate, v.status AS vehicle_status,
                   ps.id AS ps_id, ps.city AS ps_city, ps.name AS ps_name, ps.address AS ps_address,
                   ds.id AS ds_id, ds.city AS ds_city, ds.name AS ds_name, ds.address AS ds_address,
                   (SELECT count(*) FROM bookings b WHERE b.trip_id = t.id) AS bookings
            FROM trips t
            JOIN operators o ON o.id = t.operator_id
            JOIN stations ps ON ps.id = t.pickup_station_id
            JOIN stations ds ON ds.id = t.dropoff_station_id
            LEFT JOIN vehicles v ON v.id = t.vehicle_id
            """;

    static TripTemplate template(ResultSet rs) throws SQLException {
        Integer vid = (Integer) rs.getObject("vehicle_id");
        Date sd = rs.getDate("service_date");
        return new TripTemplate(rs.getString("id"), rs.getInt("route_id"), rs.getInt("operator_id"),
                rs.getString("operator_name"), rs.getString("bus_type"), rs.getString("bus_name"),
                rs.getObject("depart_time", LocalTime.class).format(HM), rs.getInt("duration_minutes"),
                rs.getInt("price"), rs.getInt("total_seats"),
                vid == null ? null : new VehicleRef(vid, rs.getString("vehicle_code"), rs.getString("plate"),
                        rs.getString("vehicle_status")),
                new StationRef(rs.getInt("ps_id"), rs.getString("ps_city"), rs.getString("ps_name"),
                        rs.getString("ps_address")),
                new StationRef(rs.getInt("ds_id"), rs.getString("ds_city"), rs.getString("ds_name"),
                        rs.getString("ds_address")),
                sd == null ? null : sd.toLocalDate(), rs.getBoolean("active"), rs.getLong("bookings"));
    }

    Optional<TripTemplate> template(String tripId) {
        return db.jdbc.query(TEMPLATE_SQL + " WHERE upper(t.id) = upper(:id)",
                AdminSupport.params().addValue("id", tripId), (rs, i) -> template(rs)).stream().findFirst();
    }

    static String money(long amount) {
        return String.format(Locale.ROOT, "%,d", amount).replace(',', '.') + "đ";
    }

    /** Gom các chuyến theo mã (dùng cho xe / tài xế: chuyến đang chạy và chuyến kế tiếp). */
    static Map<Integer, List<Run>> groupBy(List<Run> runs, java.util.function.Function<Run, Integer> key) {
        Map<Integer, List<Run>> out = new LinkedHashMap<>();
        for (Run r : runs) {
            Integer k = key.apply(r);
            if (k != null) out.computeIfAbsent(k, x -> new ArrayList<>()).add(r);
        }
        return out;
    }
}
