package com.vego.backend.admin;

import com.vego.backend.admin.AdminDtos.ActivityFeed;
import com.vego.backend.admin.AdminDtos.AssignRequest;
import com.vego.backend.admin.AdminDtos.DriverDetail;
import com.vego.backend.admin.AdminDtos.DriverList;
import com.vego.backend.admin.AdminDtos.DriverRequest;
import com.vego.backend.admin.AdminDtos.Options;
import com.vego.backend.admin.AdminDtos.Overview;
import com.vego.backend.admin.AdminDtos.RouteList;
import com.vego.backend.admin.AdminDtos.RouteRequest;
import com.vego.backend.admin.AdminDtos.RouteRow;
import com.vego.backend.admin.AdminDtos.Run;
import com.vego.backend.admin.AdminDtos.ScheduleRequest;
import com.vego.backend.admin.AdminDtos.SchedulePreview;
import com.vego.backend.admin.AdminDtos.StationRef;
import com.vego.backend.admin.AdminDtos.StationRequest;
import com.vego.backend.admin.AdminDtos.SuggestionReport;
import com.vego.backend.admin.AdminDtos.TopRoutes;
import com.vego.backend.admin.AdminDtos.TripRequest;
import com.vego.backend.admin.AdminDtos.TripTemplate;
import com.vego.backend.admin.AdminDtos.VehicleDetail;
import com.vego.backend.admin.AdminDtos.VehicleList;
import com.vego.backend.admin.AdminDtos.VehicleRequest;
import com.vego.backend.admin.ScheduleService.RunFilter;
import com.vego.backend.auth.dto.MessageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/** API cho web admin. Chỉ tài khoản role ADMIN gọi được (SecurityConfig: /api/v1/admin/**). */
@RestController
@RequestMapping("/api/v1/admin")
@Tag(name = "Quản trị")
public class AdminController {

    private final FleetService fleet;
    private final RouteAdminService routes;
    private final ScheduleService schedule;
    private final StatsService stats;
    private final AdminSupport db;

    public AdminController(FleetService fleet, RouteAdminService routes, ScheduleService schedule,
                           StatsService stats, AdminSupport db) {
        this.fleet = fleet;
        this.routes = routes;
        this.schedule = schedule;
        this.stats = stats;
        this.db = db;
    }

    // ---------- Thống kê ----------

    @Operation(summary = "Tổng quan vận hành", description = "mode = DAY | MONTH | YEAR, date = ngày bất kỳ trong kỳ")
    @GetMapping("/stats/overview")
    public Overview overview(@RequestParam(required = false) String mode,
                             @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return stats.overview(mode, date);
    }

    @Operation(summary = "Top tuyến bán chạy", description = "sort = TICKETS | REVENUE | FILL")
    @GetMapping("/stats/top-routes")
    public TopRoutes topRoutes(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                               @RequestParam(required = false) String sort) {
        return stats.topRoutes(from, to, sort);
    }

    @Operation(summary = "Hiệu quả đề xuất chuyến", description = "group = ALL | RETURNING | NEW | LAPSED")
    @GetMapping("/stats/suggestions")
    public SuggestionReport suggestions(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                        @RequestParam(required = false) String group) {
        return stats.suggestions(from, to, group);
    }

    @Operation(summary = "Hoạt động gần đây (chuông thông báo)")
    @GetMapping("/activity")
    public ActivityFeed activity() {
        return stats.activity();
    }

    // ---------- Danh mục ----------

    @Operation(summary = "Nhà xe, bến xe, thành phố, tuyến cho các ô chọn")
    @GetMapping("/options")
    public Options options() {
        return routes.options();
    }

    @Operation(summary = "Thêm bến xe / điểm đón trả")
    @PostMapping("/stations")
    @ResponseStatus(HttpStatus.CREATED)
    public StationRef createStation(@Valid @RequestBody StationRequest req) {
        return routes.createStation(req);
    }

    // ---------- Xe ----------

    @Operation(summary = "Danh sách xe", description = "type = LIMOUSINE | SLEEPER | SEATER; state = READY | RUNNING | MAINTENANCE")
    @GetMapping("/vehicles")
    public VehicleList vehicles(@RequestParam(required = false) String q, @RequestParam(required = false) String type,
                                @RequestParam(required = false) String state,
                                @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
        return fleet.vehicles(q, type, state, page, size);
    }

    @GetMapping("/vehicles/{id}")
    public VehicleDetail vehicle(@PathVariable int id) {
        return fleet.vehicle(id);
    }

    @PostMapping("/vehicles")
    @ResponseStatus(HttpStatus.CREATED)
    public VehicleDetail createVehicle(@Valid @RequestBody VehicleRequest req) {
        return fleet.createVehicle(req);
    }

    @PutMapping("/vehicles/{id}")
    public VehicleDetail updateVehicle(@PathVariable int id, @Valid @RequestBody VehicleRequest req) {
        return fleet.updateVehicle(id, req);
    }

    @DeleteMapping("/vehicles/{id}")
    public MessageResponse deleteVehicle(@PathVariable int id) {
        fleet.deleteVehicle(id);
        return MessageResponse.of("Đã xóa xe");
    }

    // ---------- Tài xế ----------

    @Operation(summary = "Danh sách tài xế", description = "state = READY | RUNNING | OFF; licenseClass = D1 | D2 | D | ...")
    @GetMapping("/drivers")
    public DriverList drivers(@RequestParam(required = false) String q, @RequestParam(required = false) String state,
                              @RequestParam(required = false) String licenseClass,
                              @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
        return fleet.drivers(q, state, licenseClass, page, size);
    }

    @GetMapping("/drivers/{id}")
    public DriverDetail driver(@PathVariable int id) {
        return fleet.driver(id);
    }

    @PostMapping("/drivers")
    @ResponseStatus(HttpStatus.CREATED)
    public DriverDetail createDriver(@Valid @RequestBody DriverRequest req) {
        return fleet.createDriver(req);
    }

    @PutMapping("/drivers/{id}")
    public DriverDetail updateDriver(@PathVariable int id, @Valid @RequestBody DriverRequest req) {
        return fleet.updateDriver(id, req);
    }

    @DeleteMapping("/drivers/{id}")
    public MessageResponse deleteDriver(@PathVariable int id) {
        fleet.deleteDriver(id);
        return MessageResponse.of("Đã xóa tài xế");
    }

    // ---------- Tuyến ----------

    @Operation(summary = "Danh sách tuyến", description = "status = ACTIVE | INACTIVE; date: ngày đếm số chuyến chạy")
    @GetMapping("/routes")
    public RouteList routes(@RequestParam(required = false) String q, @RequestParam(required = false) String status,
                            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
        return routes.routes(q, status, date, page, size);
    }

    @GetMapping("/routes/{id}")
    public RouteRow route(@PathVariable int id) {
        return routes.route(id);
    }

    @PostMapping("/routes")
    @ResponseStatus(HttpStatus.CREATED)
    public RouteRow createRoute(@Valid @RequestBody RouteRequest req) {
        return routes.createRoute(req);
    }

    @PutMapping("/routes/{id}")
    public RouteRow updateRoute(@PathVariable int id, @Valid @RequestBody RouteRequest req) {
        return routes.updateRoute(id, req);
    }

    @DeleteMapping("/routes/{id}")
    public MessageResponse deleteRoute(@PathVariable int id) {
        routes.deleteRoute(id);
        return MessageResponse.of("Đã xóa tuyến");
    }

    @Operation(summary = "Các chuyến chạy của tuyến trong một ngày")
    @GetMapping("/routes/{id}/runs")
    public List<Run> routeRuns(@PathVariable int id,
                               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return routes.runs(id, date == null ? db.today() : date);
    }

    // ---------- Chuyến cố định ----------

    @Operation(summary = "Chuyến cố định (và chuyến tăng cường sắp tới)")
    @GetMapping("/trips")
    public List<TripTemplate> trips(@RequestParam(required = false) Integer routeId) {
        return routes.trips(routeId);
    }

    @PostMapping("/trips")
    @ResponseStatus(HttpStatus.CREATED)
    public TripTemplate createTrip(@Valid @RequestBody TripRequest req) {
        return routes.createTrip(req);
    }

    @PutMapping("/trips/{id}")
    public TripTemplate updateTrip(@PathVariable String id, @Valid @RequestBody TripRequest req) {
        return routes.updateTrip(id, req);
    }

    @DeleteMapping("/trips/{id}")
    public MessageResponse deleteTrip(@PathVariable String id) {
        return MessageResponse.of(routes.deleteTrip(id) ? "Đã xóa chuyến"
                : "Chuyến đã có lịch sử bán vé nên được chuyển sang ngừng chạy");
    }

    // ---------- Lập lịch ----------

    @Operation(summary = "Lịch chạy", description = "Lọc theo routeId / vehicleId / driverId; days: số ngày tính từ from (tối đa 31)")
    @GetMapping("/schedules")
    public List<Run> schedules(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                               @RequestParam(required = false) Integer days,
                               @RequestParam(required = false) Integer routeId,
                               @RequestParam(required = false) Integer vehicleId,
                               @RequestParam(required = false) Integer driverId) {
        LocalDate start = from == null ? db.today() : from;
        int n = days == null ? 1 : Math.max(1, Math.min(days, 31));
        return schedule.runs(start, start.plusDays(n - 1), new RunFilter(routeId, vehicleId, driverId, null));
    }

    @Operation(summary = "Kiểm tra lịch trước khi lưu (trùng lịch, GPLX, giờ đến dự kiến)")
    @PostMapping("/schedules/preview")
    public SchedulePreview preview(@Valid @RequestBody ScheduleRequest req) {
        return schedule.preview(req);
    }

    @Operation(summary = "Lập lịch chuyến: phân công cho chuyến có sẵn hoặc tạo chuyến tăng cường")
    @PostMapping("/schedules")
    @ResponseStatus(HttpStatus.CREATED)
    public Run createSchedule(@Valid @RequestBody ScheduleRequest req) {
        return schedule.create(req);
    }

    @Operation(summary = "Phân công xe / tài xế cho một chuyến chạy")
    @PutMapping("/schedules/assign")
    public Run assign(@Valid @RequestBody AssignRequest req) {
        return schedule.assign(req);
    }

    @Operation(summary = "Bỏ tài xế khỏi chuyến chạy")
    @DeleteMapping("/schedules/{tripId}/{date}/driver")
    public MessageResponse unassign(@PathVariable String tripId,
                                    @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        schedule.unassignDriver(tripId, date);
        return MessageResponse.of("Đã hủy phân công tài xế");
    }

    @Operation(summary = "Xóa lịch: chuyến tăng cường chưa bán vé thì xóa, chuyến cố định thì bỏ phân công")
    @DeleteMapping("/schedules/{tripId}/{date}")
    public MessageResponse deleteSchedule(@PathVariable String tripId,
                                          @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        schedule.delete(tripId, date);
        return MessageResponse.of("Đã xóa lịch");
    }
}
