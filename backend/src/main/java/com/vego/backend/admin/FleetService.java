package com.vego.backend.admin;

import com.vego.backend.admin.AdminDtos.Deck;
import com.vego.backend.admin.AdminDtos.DriverDetail;
import com.vego.backend.admin.AdminDtos.DriverList;
import com.vego.backend.admin.AdminDtos.DriverRequest;
import com.vego.backend.admin.AdminDtos.DriverRow;
import com.vego.backend.admin.AdminDtos.DriverSummary;
import com.vego.backend.admin.AdminDtos.Run;
import com.vego.backend.admin.AdminDtos.VehicleDetail;
import com.vego.backend.admin.AdminDtos.VehicleList;
import com.vego.backend.admin.AdminDtos.VehicleRequest;
import com.vego.backend.admin.AdminDtos.VehicleRow;
import com.vego.backend.admin.AdminDtos.VehicleSummary;
import com.vego.backend.admin.ScheduleService.RunFilter;
import com.vego.backend.common.ApiException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Quản lý xe và tài xế. Trạng thái "Đang chạy" tính theo lịch thật (đang trong giờ một chuyến được phân công). */
@Service
public class FleetService {

    /** Xem trước lịch bao nhiêu ngày để tìm chuyến kế tiếp. */
    private static final int LOOKAHEAD_DAYS = 14;

    private final AdminSupport db;
    private final ScheduleService schedule;

    public FleetService(AdminSupport db, ScheduleService schedule) {
        this.db = db;
        this.schedule = schedule;
    }

    private List<Run> window() {
        LocalDate today = db.today();
        return schedule.runs(today.minusDays(2), today.plusDays(LOOKAHEAD_DAYS), RunFilter.NONE);
    }

    private static Run current(List<Run> runs) {
        return runs == null ? null : runs.stream().filter(r -> "RUNNING".equals(r.state())).findFirst().orElse(null);
    }

    private static Run next(List<Run> runs) {
        return runs == null ? null : runs.stream().filter(r -> "SCHEDULED".equals(r.state())).findFirst().orElse(null);
    }

    // =====================================================================
    // Xe
    // =====================================================================

    private record VehicleBase(int id, String code, String plate, int operatorId, String operator, String busType,
                               int totalSeats, String status, String note, List<String> defaultTrips) { }

    private List<VehicleBase> vehicleBases(Integer id) {
        String sql = """
                SELECT v.id, v.code, v.plate, v.operator_id, o.name AS operator_name, v.bus_type, v.total_seats,
                       v.status, v.note,
                       coalesce((SELECT string_agg(t.id, ',' ORDER BY t.depart_time) FROM trips t
                                  WHERE t.vehicle_id = v.id AND t.active AND t.service_date IS NULL), '') AS trips
                FROM vehicles v JOIN operators o ON o.id = v.operator_id
                """ + (id == null ? "" : " WHERE v.id = :id") + " ORDER BY v.code";
        return db.jdbc.query(sql, AdminSupport.params().addValue("id", id), (rs, i) -> new VehicleBase(
                rs.getInt("id"), rs.getString("code"), rs.getString("plate"), rs.getInt("operator_id"),
                rs.getString("operator_name"), rs.getString("bus_type"), rs.getInt("total_seats"),
                rs.getString("status"), rs.getString("note"),
                rs.getString("trips").isEmpty() ? List.of() : Arrays.asList(rs.getString("trips").split(","))));
    }

    private static VehicleRow vehicleRow(VehicleBase b, List<Run> runs) {
        Run cur = current(runs);
        String state = "MAINTENANCE".equals(b.status()) ? "MAINTENANCE" : cur != null ? "RUNNING" : "READY";
        return new VehicleRow(b.id(), b.code(), b.plate(), b.operatorId(), b.operator(), b.busType(),
                AdminSupport.typeLabel(b.busType(), b.totalSeats()), b.totalSeats(), b.status(), state, cur,
                next(runs), b.defaultTrips(), b.note());
    }

    @Transactional(readOnly = true)
    public VehicleList vehicles(String q, String type, String state, Integer page, Integer size) {
        Map<Integer, List<Run>> byVehicle = ScheduleService.groupBy(window(),
                r -> r.vehicle() == null ? null : r.vehicle().id());
        List<VehicleRow> all = vehicleBases(null).stream()
                .map(b -> vehicleRow(b, byVehicle.get(b.id()))).toList();
        VehicleSummary summary = new VehicleSummary(all.size(),
                all.stream().filter(v -> "READY".equals(v.state())).count(),
                all.stream().filter(v -> "RUNNING".equals(v.state())).count(),
                all.stream().filter(v -> "MAINTENANCE".equals(v.state())).count());
        List<VehicleRow> filtered = all.stream()
                .filter(v -> AdminSupport.matches(q, v.code(), v.plate(), v.typeLabel(), v.operator()))
                .filter(v -> type == null || type.isBlank() || v.busType().equalsIgnoreCase(type))
                .filter(v -> state == null || state.isBlank() || v.state().equalsIgnoreCase(state))
                .toList();
        return new VehicleList(summary, AdminSupport.page(filtered, page, size));
    }

    @Transactional(readOnly = true)
    public VehicleDetail vehicle(int id) {
        VehicleBase b = vehicleBases(id).stream().findFirst()
                .orElseThrow(() -> ApiException.notFound("VEHICLE_NOT_FOUND", "Không tìm thấy xe"));
        LocalDate today = db.today();
        List<Run> runs = schedule.runs(today.minusDays(2), today.plusDays(LOOKAHEAD_DAYS),
                new RunFilter(null, id, null, null));
        return new VehicleDetail(vehicleRow(b, runs), decks(b.busType(), b.totalSeats()),
                runs.stream().filter(r -> !"DONE".equals(r.state())).limit(10).toList());
    }

    /** Sơ đồ ghế theo đúng quy tắc của app (SeatInventory). */
    static List<Deck> decks(String busType, int total) {
        boolean twoDecks = !"SEATER".equals(busType) && total >= 20;
        if (!twoDecks) return List.of(deck("A", "Khoang hành khách", total));
        int lower = (total + 1) / 2;
        return List.of(deck("A", "Tầng dưới", lower), deck("B", "Tầng trên", total - lower));
    }

    private static Deck deck(String prefix, String name, int count) {
        List<String> seats = new ArrayList<>();
        for (int n = 1; n <= count; n++) seats.add(String.format(Locale.ROOT, "%s%02d", prefix, n));
        return new Deck(prefix, name, seats);
    }

    @Transactional
    public VehicleDetail createVehicle(VehicleRequest req) {
        String code = AdminSupport.trimToNull(req.code());
        code = code == null ? db.nextCode("vehicles", "XE") : code.toUpperCase(Locale.ROOT);
        requireOperator(req.operatorId());
        Integer id;
        try {
            id = db.jdbc.queryForObject("""
                    INSERT INTO vehicles (code, plate, operator_id, bus_type, total_seats, status, note)
                    VALUES (:code, :plate, :operatorId, :busType, :seats, :status, :note) RETURNING id
                    """, vehicleParams(req).addValue("code", code), Integer.class);
        } catch (DuplicateKeyException e) {
            throw duplicateVehicle(e);
        }
        return vehicle(id);
    }

    @Transactional
    public VehicleDetail updateVehicle(int id, VehicleRequest req) {
        VehicleBase old = vehicleBases(id).stream().findFirst()
                .orElseThrow(() -> ApiException.notFound("VEHICLE_NOT_FOUND", "Không tìm thấy xe"));
        requireOperator(req.operatorId());
        boolean layoutChanged = !old.busType().equals(req.busType()) || old.totalSeats() != req.totalSeats();
        boolean operatorChanged = old.operatorId() != req.operatorId();
        if (layoutChanged || operatorChanged) {
            List<Run> upcoming = schedule.runs(db.today(), db.today().plusDays(60), new RunFilter(null, id, null, null))
                    .stream().filter(r -> !"DONE".equals(r.state())).toList();
            if (!upcoming.isEmpty()) {
                throw AdminSupport.conflict("VEHICLE_IN_USE", "Xe " + old.code() + " đang có lịch chạy (chuyến "
                        + upcoming.get(0).code() + "); đổi xe khác cho các chuyến này trước khi sửa "
                        + (layoutChanged ? "loại xe / số chỗ" : "nhà xe"));
            }
        }
        String code = AdminSupport.trimToNull(req.code());
        code = code == null ? old.code() : code.toUpperCase(Locale.ROOT);
        try {
            db.jdbc.update("""
                    UPDATE vehicles SET code = :code, plate = :plate, operator_id = :operatorId, bus_type = :busType,
                           total_seats = :seats, status = :status, note = :note, updated_at = now()
                    WHERE id = :id
                    """, vehicleParams(req).addValue("code", code).addValue("id", id));
        } catch (DuplicateKeyException e) {
            throw duplicateVehicle(e);
        }
        return vehicle(id);
    }

    @Transactional
    public void deleteVehicle(int id) {
        VehicleBase b = vehicleBases(id).stream().findFirst()
                .orElseThrow(() -> ApiException.notFound("VEHICLE_NOT_FOUND", "Không tìm thấy xe"));
        if (!b.defaultTrips().isEmpty()) {
            throw AdminSupport.conflict("VEHICLE_IN_USE", "Xe " + b.code() + " là xe mặc định của chuyến "
                    + String.join(", ", b.defaultTrips()) + "; hãy chuyển sang trạng thái Bảo trì nếu tạm ngừng");
        }
        MapSqlParameterSource p = AdminSupport.params().addValue("id", id).addValue("today", Date.valueOf(db.today()));
        if (db.exists("SELECT 1 FROM trip_assignments WHERE vehicle_id = :id AND travel_date >= :today", p)
                || db.exists("SELECT 1 FROM trips WHERE vehicle_id = :id AND active AND service_date >= :today", p)) {
            throw AdminSupport.conflict("VEHICLE_IN_USE", "Xe " + b.code()
                    + " đang được gán lịch chạy; hãy chuyển sang trạng thái Bảo trì nếu tạm ngừng");
        }
        db.jdbc.update("UPDATE trip_assignments SET vehicle_id = NULL WHERE vehicle_id = :id", p);
        db.jdbc.update("UPDATE trips SET vehicle_id = NULL WHERE vehicle_id = :id", p);
        db.jdbc.update("DELETE FROM vehicles WHERE id = :id", p);
    }

    private MapSqlParameterSource vehicleParams(VehicleRequest req) {
        return AdminSupport.params()
                .addValue("plate", normalizePlate(req.plate()))
                .addValue("operatorId", req.operatorId())
                .addValue("busType", req.busType())
                .addValue("seats", req.totalSeats())
                .addValue("status", req.status() == null ? "READY" : req.status())
                .addValue("note", AdminSupport.trimToNull(req.note()));
    }

    /** "51b 12345" -> "51B-123.45". */
    static String normalizePlate(String raw) {
        String s = raw.toUpperCase(Locale.ROOT).replaceAll("[\\s.\\-]", "");
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("^(\\d{2}[A-Z]{1,2}\\d?)(\\d{3})(\\d{2})$").matcher(s);
        if (!m.matches()) throw ApiException.field("INVALID_PLATE", "plate", "Biển số chưa đúng, vd 51B-123.45");
        return m.group(1) + "-" + m.group(2) + "." + m.group(3);
    }

    private void requireOperator(Integer id) {
        if (!db.exists("SELECT 1 FROM operators WHERE id = :id", AdminSupport.params().addValue("id", id))) {
            throw ApiException.field("OPERATOR_NOT_FOUND", "operatorId", "Không tìm thấy nhà xe");
        }
    }

    private static ApiException duplicateVehicle(DuplicateKeyException e) {
        String msg = String.valueOf(e.getMostSpecificCause().getMessage());
        if (msg.contains("plate")) return ApiException.conflict("PLATE_EXISTS", "plate", "Biển số này đã có trong đội xe");
        return ApiException.conflict("CODE_EXISTS", "code", "Mã xe này đã được dùng");
    }

    // =====================================================================
    // Tài xế
    // =====================================================================

    private record DriverBase(int id, String code, String fullName, String phone, String licenseNo,
                              String licenseClass, LocalDate licenseExpiry, int experienceYears, String status,
                              String note, long tripsThisMonth) { }

    private List<DriverBase> driverBases(Integer id) {
        LocalDate today = db.today();
        String sql = """
                SELECT d.*,
                       (SELECT count(*) FROM trip_assignments a JOIN trips t ON t.id = a.trip_id
                         WHERE a.driver_id = d.id AND a.travel_date BETWEEN :monthStart AND :today) AS month_trips
                FROM drivers d
                """ + (id == null ? "" : " WHERE d.id = :id") + " ORDER BY d.code";
        return db.jdbc.query(sql, AdminSupport.params().addValue("id", id)
                .addValue("monthStart", Date.valueOf(today.withDayOfMonth(1))).addValue("today", Date.valueOf(today)),
                (rs, i) -> new DriverBase(rs.getInt("id"), rs.getString("code"), rs.getString("full_name"),
                        rs.getString("phone"), rs.getString("license_no"), rs.getString("license_class"),
                        rs.getDate("license_expiry").toLocalDate(), rs.getInt("experience_years"),
                        rs.getString("status"), rs.getString("note"), rs.getLong("month_trips")));
    }

    private DriverRow driverRow(DriverBase b, List<Run> runs) {
        Run cur = current(runs);
        String state = "OFF".equals(b.status()) ? "OFF" : cur != null ? "RUNNING" : "READY";
        return new DriverRow(b.id(), b.code(), b.fullName(), b.phone(), b.licenseNo(), b.licenseClass(),
                b.licenseExpiry(), !b.licenseExpiry().isBefore(db.today()), AdminSupport.maxSeats(b.licenseClass()),
                b.experienceYears(), b.status(), state, cur, next(runs), b.tripsThisMonth(), b.note());
    }

    @Transactional(readOnly = true)
    public DriverList drivers(String q, String state, String licenseClass, Integer page, Integer size) {
        Map<Integer, List<Run>> byDriver = ScheduleService.groupBy(window(),
                r -> r.driver() == null ? null : r.driver().id());
        List<DriverRow> all = driverBases(null).stream().map(b -> driverRow(b, byDriver.get(b.id()))).toList();
        DriverSummary summary = new DriverSummary(all.size(),
                all.stream().filter(d -> "READY".equals(d.state())).count(),
                all.stream().filter(d -> "RUNNING".equals(d.state())).count(),
                all.stream().filter(d -> "OFF".equals(d.state())).count());
        List<DriverRow> filtered = all.stream()
                .filter(d -> AdminSupport.matches(q, d.code(), d.fullName(), d.phone(), d.licenseNo()))
                .filter(d -> state == null || state.isBlank() || d.state().equalsIgnoreCase(state))
                .filter(d -> licenseClass == null || licenseClass.isBlank() || d.licenseClass().equalsIgnoreCase(licenseClass))
                .toList();
        return new DriverList(summary, AdminSupport.page(filtered, page, size));
    }

    @Transactional(readOnly = true)
    public DriverDetail driver(int id) {
        DriverBase b = driverBases(id).stream().findFirst()
                .orElseThrow(() -> ApiException.notFound("DRIVER_NOT_FOUND", "Không tìm thấy tài xế"));
        LocalDate today = db.today();
        List<Run> runs = schedule.runs(today.minusDays(2), today.plusDays(LOOKAHEAD_DAYS),
                new RunFilter(null, null, id, null));
        return new DriverDetail(driverRow(b, runs), runs.stream().filter(r -> !"DONE".equals(r.state())).toList());
    }

    @Transactional
    public DriverDetail createDriver(DriverRequest req) {
        String code = AdminSupport.trimToNull(req.code());
        code = code == null ? db.nextCode("drivers", "TX") : code.toUpperCase(Locale.ROOT);
        Integer id;
        try {
            id = db.jdbc.queryForObject("""
                    INSERT INTO drivers (code, full_name, phone, license_no, license_class, license_expiry,
                                         experience_years, status, note)
                    VALUES (:code, :name, :phone, :licenseNo, :licenseClass, :expiry, :exp, :status, :note)
                    RETURNING id
                    """, driverParams(req).addValue("code", code), Integer.class);
        } catch (DuplicateKeyException e) {
            throw duplicateDriver(e);
        }
        return driver(id);
    }

    @Transactional
    public DriverDetail updateDriver(int id, DriverRequest req) {
        DriverBase old = driverBases(id).stream().findFirst()
                .orElseThrow(() -> ApiException.notFound("DRIVER_NOT_FOUND", "Không tìm thấy tài xế"));
        String code = AdminSupport.trimToNull(req.code());
        code = code == null ? old.code() : code.toUpperCase(Locale.ROOT);
        try {
            db.jdbc.update("""
                    UPDATE drivers SET code = :code, full_name = :name, phone = :phone, license_no = :licenseNo,
                           license_class = :licenseClass, license_expiry = :expiry, experience_years = :exp,
                           status = :status, note = :note, updated_at = now()
                    WHERE id = :id
                    """, driverParams(req).addValue("code", code).addValue("id", id));
        } catch (DuplicateKeyException e) {
            throw duplicateDriver(e);
        }
        return driver(id);
    }

    @Transactional
    public void deleteDriver(int id) {
        DriverBase b = driverBases(id).stream().findFirst()
                .orElseThrow(() -> ApiException.notFound("DRIVER_NOT_FOUND", "Không tìm thấy tài xế"));
        Instant now = db.now();
        List<Run> upcoming = schedule.runs(db.today().minusDays(1), db.today().plusDays(60),
                new RunFilter(null, null, id, null)).stream().filter(r -> r.arriveAt().isAfter(now)).toList();
        if (!upcoming.isEmpty()) {
            throw AdminSupport.conflict("DRIVER_IN_USE", "Tài xế " + b.fullName() + " đang được gán chuyến "
                    + upcoming.get(0).code() + "; hủy phân công hoặc chuyển sang Tạm nghỉ trước");
        }
        MapSqlParameterSource p = AdminSupport.params().addValue("id", id);
        db.jdbc.update("UPDATE trip_assignments SET driver_id = NULL WHERE driver_id = :id", p);
        db.jdbc.update("DELETE FROM drivers WHERE id = :id", p);
    }

    private MapSqlParameterSource driverParams(DriverRequest req) {
        return AdminSupport.params()
                .addValue("name", req.fullName().trim())
                .addValue("phone", AdminSupport.normalizePhone(req.phone()))
                .addValue("licenseNo", req.licenseNo().trim())
                .addValue("licenseClass", req.licenseClass())
                .addValue("expiry", Date.valueOf(req.licenseExpiry()))
                .addValue("exp", req.experienceYears() == null ? 0 : req.experienceYears())
                .addValue("status", req.status() == null ? "READY" : req.status())
                .addValue("note", AdminSupport.trimToNull(req.note()));
    }

    private static ApiException duplicateDriver(DuplicateKeyException e) {
        String msg = String.valueOf(e.getMostSpecificCause().getMessage());
        if (msg.contains("phone")) return ApiException.conflict("PHONE_EXISTS", "phone", "Số điện thoại đã được dùng");
        if (msg.contains("license_no")) {
            return ApiException.conflict("LICENSE_EXISTS", "licenseNo", "Số GPLX này đã có trong danh sách");
        }
        return ApiException.conflict("CODE_EXISTS", "code", "Mã tài xế này đã được dùng");
    }
}
