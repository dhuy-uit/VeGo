package com.vego.backend.admin;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Dữ liệu vào / ra của API web admin (/api/v1/admin/**). */
public final class AdminDtos {

    private AdminDtos() { }

    /** Trang kết quả: items + tổng số dòng. */
    public record Page<T>(List<T> items, int page, int size, long total) { }

    // ---------- Chuyến chạy trong một ngày ----------

    public record VehicleRef(int id, String code, String plate, String status) { }

    public record DriverRef(int id, String code, String fullName, String phone) { }

    /**
     * Một chuyến chạy vào một ngày cụ thể. code = mã chuyến + ngày, vd PT2200-1811.
     * seatsSold: ngày đã qua = vé đã thanh toán; hôm nay trở đi = số ghế không còn bán được (giống app).
     */
    public record Run(String code, String tripId, LocalDate date, int routeId, String routeCode, String routeName,
                      String operator, String busType, Instant departAt, Instant arriveAt, String departTime,
                      String arriveTime, int arriveDayOffset, int price, int seatsTotal, int seatsSold,
                      boolean oneOff, VehicleRef vehicle, DriverRef driver, String note, String state) { }

    // ---------- Xe ----------

    public record VehicleSummary(long total, long ready, long running, long maintenance) { }

    public record Deck(String code, String name, List<String> seats) { }

    public record VehicleRow(int id, String code, String plate, int operatorId, String operator, String busType,
                             String typeLabel, int totalSeats, String status, String state, Run currentRun,
                             Run nextRun, List<String> defaultTrips, String note) { }

    public record VehicleDetail(VehicleRow vehicle, List<Deck> decks, List<Run> runs) { }

    public record VehicleList(VehicleSummary summary, Page<VehicleRow> page) { }

    public record VehicleRequest(
            @Size(max = 10, message = "Mã xe tối đa 10 ký tự")
            @Pattern(regexp = "^$|^[A-Za-z0-9-]+$", message = "Mã xe chỉ gồm chữ, số và dấu -")
            String code,
            @NotBlank(message = "Vui lòng nhập biển số")
            @Pattern(regexp = "^\\s*\\d{2}[A-Za-z]{1,2}\\d?-?\\s*\\d{3}\\.?\\d{2}\\s*$",
                    message = "Biển số chưa đúng, vd 51B-123.45")
            String plate,
            @NotNull(message = "Vui lòng chọn nhà xe") Integer operatorId,
            @NotBlank(message = "Vui lòng chọn loại xe")
            @Pattern(regexp = "LIMOUSINE|SLEEPER|SEATER", message = "Loại xe không hợp lệ")
            String busType,
            @NotNull(message = "Vui lòng nhập tổng số chỗ")
            @Min(value = 4, message = "Xe cần ít nhất 4 chỗ") @Max(value = 60, message = "Tối đa 60 chỗ")
            Integer totalSeats,
            @Pattern(regexp = "READY|MAINTENANCE", message = "Trạng thái không hợp lệ") String status,
            @Size(max = 500, message = "Ghi chú tối đa 500 ký tự") String note) { }

    // ---------- Tài xế ----------

    public record DriverSummary(long total, long ready, long running, long off) { }

    public record DriverRow(int id, String code, String fullName, String phone, String licenseNo,
                            String licenseClass, LocalDate licenseExpiry, boolean licenseValid, int maxSeats,
                            int experienceYears, String status, String state, Run currentRun, Run nextRun,
                            long tripsThisMonth, String note) { }

    public record DriverDetail(DriverRow driver, List<Run> runs) { }

    public record DriverList(DriverSummary summary, Page<DriverRow> page) { }

    public record DriverRequest(
            @Size(max = 10, message = "Mã tài xế tối đa 10 ký tự")
            @Pattern(regexp = "^$|^[A-Za-z0-9-]+$", message = "Mã tài xế chỉ gồm chữ, số và dấu -")
            String code,
            @NotBlank(message = "Vui lòng nhập họ và tên") @Size(max = 100, message = "Họ tên tối đa 100 ký tự")
            String fullName,
            @NotBlank(message = "Vui lòng nhập số điện thoại") String phone,
            @NotBlank(message = "Vui lòng nhập số giấy phép lái xe")
            @Pattern(regexp = "^\\s*\\d{12}\\s*$", message = "Số GPLX gồm 12 chữ số")
            String licenseNo,
            @NotBlank(message = "Vui lòng chọn hạng GPLX")
            @Pattern(regexp = "D1|D2|D|D1E|D2E|DE", message = "Hạng GPLX chở khách: D1, D2, D, D1E, D2E, DE")
            String licenseClass,
            @NotNull(message = "Vui lòng nhập ngày hết hạn GPLX") LocalDate licenseExpiry,
            @Min(value = 0, message = "Số năm kinh nghiệm không hợp lệ")
            @Max(value = 60, message = "Số năm kinh nghiệm không hợp lệ") Integer experienceYears,
            @Pattern(regexp = "READY|OFF", message = "Trạng thái không hợp lệ") String status,
            @Size(max = 500, message = "Ghi chú tối đa 500 ký tự") String note) { }

    // ---------- Tuyến / chuyến ----------

    public record StationRef(int id, String city, String name, String address) { }

    public record RouteRow(int id, String code, String originCity, String destinationCity, String name,
                           Integer durationMinutes, Integer distanceKm, boolean active, StationRef originStation,
                           StationRef destinationStation, int tripTemplates, int runsOnDate) { }

    public record RouteList(int totalRoutes, int activeRoutes, int runsOnDate, LocalDate date,
                            Page<RouteRow> page) { }

    public record RouteRequest(
            @Size(max = 10, message = "Mã tuyến tối đa 10 ký tự")
            @Pattern(regexp = "^$|^[A-Za-z0-9-]+$", message = "Mã tuyến chỉ gồm chữ, số và dấu -")
            String code,
            @NotBlank(message = "Vui lòng nhập điểm đi") @Size(max = 100) String originCity,
            @NotBlank(message = "Vui lòng nhập điểm đến") @Size(max = 100) String destinationCity,
            @NotNull(message = "Vui lòng nhập thời lượng dự kiến")
            @Min(value = 15, message = "Thời lượng tối thiểu 15 phút")
            @Max(value = 2880, message = "Thời lượng tối đa 48 giờ") Integer durationMinutes,
            @Min(value = 1, message = "Khoảng cách không hợp lệ") Integer distanceKm,
            Boolean active,
            @NotNull(message = "Vui lòng chọn bến xuất phát") Integer originStationId,
            @NotNull(message = "Vui lòng chọn bến đến") Integer destinationStationId) { }

    public record TripTemplate(String id, int routeId, int operatorId, String operator, String busType,
                               String busName, String departTime, int durationMinutes, int price, int totalSeats,
                               VehicleRef vehicle, StationRef pickupStation, StationRef dropoffStation,
                               LocalDate serviceDate, boolean active, long bookings) { }

    public record TripRequest(
            @NotNull(message = "Vui lòng chọn tuyến") Integer routeId,
            @NotBlank(message = "Vui lòng nhập giờ khởi hành")
            @Pattern(regexp = "^([01]\\d|2[0-3]):[0-5]\\d$", message = "Giờ khởi hành dạng HH:mm")
            String departTime,
            @Min(value = 15, message = "Thời lượng tối thiểu 15 phút")
            @Max(value = 2880, message = "Thời lượng tối đa 48 giờ") Integer durationMinutes,
            @NotNull(message = "Vui lòng nhập giá vé")
            @Min(value = 10000, message = "Giá vé tối thiểu 10.000đ")
            @Max(value = 5000000, message = "Giá vé tối đa 5.000.000đ") Integer price,
            @NotNull(message = "Vui lòng chọn xe") Integer vehicleId,
            Integer pickupStationId,
            Integer dropoffStationId,
            Boolean active) { }

    public record StationRequest(
            @NotBlank(message = "Vui lòng nhập thành phố") @Size(max = 100) String city,
            @NotBlank(message = "Vui lòng nhập tên bến") @Size(max = 150) String name,
            @NotBlank(message = "Vui lòng nhập địa chỉ") @Size(max = 255) String address) { }

    public record Option(int id, String code, String name) { }

    public record Options(List<Option> operators, List<StationRef> stations, List<String> cities,
                          List<Option> routes) { }

    // ---------- Lập lịch ----------

    public record ScheduleRequest(
            @NotNull(message = "Vui lòng chọn tuyến") Integer routeId,
            @NotNull(message = "Vui lòng chọn ngày khởi hành") LocalDate date,
            @NotBlank(message = "Vui lòng nhập giờ khởi hành")
            @Pattern(regexp = "^([01]\\d|2[0-3]):[0-5]\\d$", message = "Giờ khởi hành dạng HH:mm")
            String departTime,
            @NotNull(message = "Vui lòng chọn xe") Integer vehicleId,
            Integer driverId,
            @Min(value = 10000, message = "Giá vé tối thiểu 10.000đ")
            @Max(value = 5000000, message = "Giá vé tối đa 5.000.000đ") Integer price,
            @Min(value = 15, message = "Thời lượng tối thiểu 15 phút")
            @Max(value = 2880, message = "Thời lượng tối đa 48 giờ") Integer durationMinutes,
            @Size(max = 300, message = "Ghi chú tối đa 300 ký tự") String note) { }

    /** Kiểm tra trước khi lưu: chuyến cố định trùng giờ (nếu có), giờ đến dự kiến, lỗi và cảnh báo. */
    public record SchedulePreview(TripTemplate existingTrip, Instant departAt, Instant arriveAt, int price,
                                  int seats, List<String> errors, List<String> warnings, List<Run> vehicleRuns) { }

    public record AssignRequest(
            @NotBlank(message = "Vui lòng chọn chuyến") String tripId,
            @NotNull(message = "Vui lòng chọn ngày") LocalDate date,
            Integer vehicleId,
            Integer driverId,
            @Size(max = 300, message = "Ghi chú tối đa 300 ký tự") String note) { }

    // ---------- Thống kê ----------

    public record Point(String label, long revenue, long tickets) { }

    public record RouteStat(int rank, int routeId, String code, String name, long tickets, long seats,
                            double fillRate, long revenue) { }

    public record Overview(String mode, LocalDate from, LocalDate to, long revenue, long revenuePrev, long tickets,
                           long ticketsPrev, long seats, double fillRate, long trips, double tripsPerDay,
                           String seriesUnit, List<Point> series, List<RouteStat> topRoutes, int routeCount,
                           boolean includesDemo) { }

    public record Highlight(String code, String tripId, LocalDate date, String operator, String routeName,
                            Instant departAt, long sold, int total, double fillRate) { }

    public record TopRoutes(LocalDate from, LocalDate to, String sort, long totalTickets, long totalSeats,
                            long totalRevenue, long avgPrice, RouteStat leader, List<RouteStat> items,
                            Highlight highlight, boolean includesDemo) { }

    public record WeekPoint(LocalDate weekStart, long impressions, long clicks, double ctr) { }

    public record GroupStat(String group, String label, long impressions, long clicks, long bookings, double ctr) { }

    public record ModelInfo(String name, String version, String state, int windowDays, Instant updatedAt,
                            String description) { }

    public record SuggestionReport(LocalDate from, LocalDate to, String group, long impressions,
                                   long impressionsPrev, long clicks, double ctr, long bookings,
                                   double conversion, long revenue, double revenueShare, List<WeekPoint> weekly,
                                   List<GroupStat> groups, ModelInfo model, boolean includesDemo) { }

    public record Activity(String kind, String title, String detail, Instant at) { }

    public record ActivityFeed(long last24h, List<Activity> items) { }
}
