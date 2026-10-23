package com.vego.backend.trip;

import com.vego.backend.common.ApiException;
import com.vego.backend.config.AppProperties;
import com.vego.backend.home.HomeService;
import com.vego.backend.home.Route;
import com.vego.backend.home.RouteRepository;
import com.vego.backend.home.Station;
import com.vego.backend.home.Trip;
import com.vego.backend.home.TripRepository;
import com.vego.backend.home.dto.TripResponse;
import com.vego.backend.trip.dto.SeatMapResponse;
import com.vego.backend.trip.dto.StationResponse;
import com.vego.backend.trip.dto.TripSearchResponse;
import com.vego.backend.trip.dto.TripStopsResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Tìm chuyến, chi tiết chuyến, sơ đồ ghế và điểm đón / trả. */
@Service
public class TripService {

    /** Không bán vé chuyến khởi hành trong vòng 30 phút tới (giống BookingService). */
    static final Duration MIN_LEAD_TIME = Duration.ofMinutes(30);
    static final int MAX_DAYS_AHEAD = 60;

    /** Bộ lọc / sắp xếp tùy chọn (null hoặc rỗng = không lọc theo tiêu chí đó). */
    public record Filter(Collection<String> operators, Collection<String> busTypes, Collection<Integer> timeSlots,
                         Integer minPrice, Integer maxPrice, String sort) {

        public static final Filter NONE = new Filter(null, null, null, null, null, null);

        boolean matches(Trip t) {
            if (operators != null && !operators.isEmpty() && operators.stream()
                    .noneMatch(o -> o.equalsIgnoreCase(t.getOperator().getName()))) return false;
            if (busTypes != null && !busTypes.isEmpty() && busTypes.stream()
                    .noneMatch(b -> b.equalsIgnoreCase(t.getBusType().name()))) return false;
            if (timeSlots != null && !timeSlots.isEmpty()
                    && !timeSlots.contains(TripTimes.departMinutes(t) / (6 * 60))) return false;
            if (minPrice != null && t.getPrice() < minPrice) return false;
            return maxPrice == null || t.getPrice() <= maxPrice;
        }
    }

    private final RouteRepository routes;
    private final TripRepository trips;
    private final StationRepository stations;
    private final SeatInventory seats;
    private final ZoneId zone;
    private final Clock clock;

    public TripService(RouteRepository routes, TripRepository trips, StationRepository stations,
                       SeatInventory seats, AppProperties props, Clock clock) {
        this.routes = routes;
        this.trips = trips;
        this.stations = stations;
        this.seats = seats;
        this.zone = props.zone();
        this.clock = clock;
    }

    // ---------- Tìm chuyến ----------

    @Transactional(readOnly = true)
    public TripSearchResponse search(String origin, String destination, LocalDate date, Filter filter) {
        if (CityNames.normalize(origin).equals(CityNames.normalize(destination))) {
            throw ApiException.field("SAME_PLACE", "destination", "Điểm đến phải khác điểm đi");
        }
        checkDate(date);

        Optional<Route> route = findRoute(origin, destination);
        if (route.isEmpty()) {
            return new TripSearchResponse(origin.trim(), destination.trim(), date, false, 0, List.of(), List.of(),
                    null, null);
        }
        Route r = route.get();
        Instant earliest = clock.instant().plus(MIN_LEAD_TIME);
        List<Trip> routeTrips = trips.findActiveByRouteIds(List.of(r.getId())).stream()
                .filter(t -> TripTimes.departure(t, date, zone).isAfter(earliest))
                .toList();

        List<String> operators = routeTrips.stream().map(t -> t.getOperator().getName()).distinct().sorted().toList();
        Integer minPrice = routeTrips.stream().map(Trip::getPrice).min(Integer::compare).orElse(null);
        Integer maxPrice = routeTrips.stream().map(Trip::getPrice).max(Integer::compare).orElse(null);

        Filter f = filter == null ? Filter.NONE : filter;
        List<Trip> matched = routeTrips.stream().filter(f::matches).toList();
        Map<String, Integer> left = seats.seatsLeft(matched, date);
        List<TripResponse> result = new ArrayList<>(matched.stream()
                .map(t -> HomeService.toTrip(t, date, left.getOrDefault(t.getId(), 0)))
                .toList());
        result.sort(comparator(f.sort()));

        return new TripSearchResponse(r.getOriginCity(), r.getDestinationCity(), date, true, result.size(),
                result, operators, minPrice, maxPrice);
    }

    /** Mặc định "Đề xuất": còn ghế trước, đánh giá cao, rồi giờ đi sớm. */
    private static Comparator<TripResponse> comparator(String sort) {
        String s = sort == null ? "" : sort.trim().toUpperCase(Locale.ROOT);
        return switch (s) {
            case "DEPARTURE" -> Comparator.comparingInt(TripResponse::departMinutes);
            case "PRICE" -> Comparator.comparingInt(TripResponse::price).thenComparingInt(TripResponse::departMinutes);
            case "RATING" -> Comparator.comparingDouble(TripResponse::rating).reversed()
                    .thenComparingInt(TripResponse::departMinutes);
            default -> Comparator.<TripResponse>comparingInt(t -> t.seatsLeft() > 0 ? 0 : 1)
                    .thenComparing(Comparator.comparingDouble(TripResponse::rating).reversed())
                    .thenComparingInt(TripResponse::departMinutes);
        };
    }

    private Optional<Route> findRoute(String origin, String destination) {
        return routes.findAll().stream()
                .filter(r -> CityNames.matches(origin, r.getOriginCity())
                        && CityNames.matches(destination, r.getDestinationCity()))
                // Khớp đúng tên trước khớp một phần
                .min(Comparator.comparingInt(r -> exactness(origin, r.getOriginCity())
                        + exactness(destination, r.getDestinationCity())));
    }

    private static int exactness(String query, String city) {
        return CityNames.normalize(query).equals(CityNames.normalize(city)) ? 0 : 1;
    }

    /** Các thành phố có tuyến (gợi ý khi gõ điểm đi / đến). */
    @Transactional(readOnly = true)
    public List<String> cities() {
        return routes.findAll().stream()
                .flatMap(r -> java.util.stream.Stream.of(r.getOriginCity(), r.getDestinationCity()))
                .distinct().sorted().toList();
    }

    // ---------- Một chuyến ----------

    @Transactional(readOnly = true)
    public TripResponse get(String tripId, LocalDate date) {
        checkDate(date);
        Trip t = load(tripId);
        return HomeService.toTrip(t, date, seats.seatsLeft(t, date));
    }

    @Transactional(readOnly = true)
    public SeatMapResponse seatMap(String tripId, LocalDate date) {
        Trip t = load(tripId);
        Set<String> taken = seats.unavailable(t, date);
        List<SeatMapResponse.Deck> decks = seats.layout(t).stream()
                .map(d -> new SeatMapResponse.Deck(d.code(), d.name(), d.rows().stream()
                        .map(row -> row.stream().map(code -> new SeatMapResponse.Seat(code, !taken.contains(code))).toList())
                        .toList()))
                .toList();
        // Chỉ liệt kê ghế có trong sơ đồ (đơn cũ có mã ghế lạ thì bỏ qua)
        List<String> takenList = seats.allSeats(t).stream().filter(taken::contains).toList();
        return new SeatMapResponse(t.getId(), date, t.getBusType().name(), t.getTotalSeats(),
                t.getTotalSeats() - takenList.size(), t.getPrice(), decks, takenList);
    }

    @Transactional(readOnly = true)
    public TripStopsResponse stops(String tripId) {
        Trip t = load(tripId);
        return new TripStopsResponse(t.getId(),
                stopsOf(t.getRoute().getOriginCity(), t.getPickupStation()),
                stopsOf(t.getRoute().getDestinationCity(), t.getDropoffStation()));
    }

    private List<StationResponse> stopsOf(String city, Station main) {
        List<StationResponse> out = new ArrayList<>();
        out.add(toStation(main));
        for (Station s : stations.findByCityOrderByIdAsc(city)) {
            if (!s.getId().equals(main.getId())) out.add(toStation(s));
        }
        return out;
    }

    @Transactional(readOnly = true)
    public List<StationResponse> stations(String city) {
        List<Station> list = city == null || city.isBlank()
                ? stations.findAllByOrderByCityAscIdAsc()
                : stations.findAllByOrderByCityAscIdAsc().stream()
                .filter(s -> CityNames.matches(city, s.getCity())).toList();
        return list.stream().map(TripService::toStation).toList();
    }

    static StationResponse toStation(Station s) {
        return new StationResponse(s.getId(), s.getCity(), s.getName(), s.getAddress());
    }

    private Trip load(String tripId) {
        return trips.findActiveWithDetails(tripId.trim())
                .orElseThrow(() -> ApiException.notFound("TRIP_NOT_FOUND", "Không tìm thấy chuyến xe"));
    }

    private void checkDate(LocalDate date) {
        LocalDate today = LocalDate.now(clock.withZone(zone));
        if (date.isBefore(today)) {
            throw ApiException.field("DATE_IN_PAST", "date", "Ngày đi đã qua, vui lòng chọn ngày khác");
        }
        if (date.isAfter(today.plusDays(MAX_DAYS_AHEAD))) {
            throw ApiException.field("DATE_TOO_FAR", "date", "Chỉ đặt vé trước tối đa " + MAX_DAYS_AHEAD + " ngày");
        }
    }
}
