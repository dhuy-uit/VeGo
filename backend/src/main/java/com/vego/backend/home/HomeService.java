package com.vego.backend.home;

import com.vego.backend.config.AppProperties;
import com.vego.backend.home.dto.HomeResponse;
import com.vego.backend.home.dto.PopularRouteResponse;
import com.vego.backend.home.dto.PromotionResponse;
import com.vego.backend.home.dto.TripResponse;
import com.vego.backend.user.User;
import com.vego.backend.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Trang chủ: lời chào, khuyến mãi đang chạy, tuyến phổ biến và một chuyến gợi ý.
 * Chuyến gợi ý = chuyến còn ghế, khởi hành sớm nhất (cách hiện tại ít nhất 30 phút)
 * trên tuyến phổ biến nhất; bằng giờ thì ưu tiên đánh giá cao hơn rồi giá rẻ hơn.
 */
@Service
public class HomeService {

    /** Không gợi ý chuyến sắp chạy trong 30 phút tới (không kịp ra bến). */
    private static final Duration MIN_LEAD_TIME = Duration.ofMinutes(30);

    private final RouteRepository routes;
    private final TripRepository trips;
    private final PromotionRepository promotions;
    private final UserRepository users;
    private final ZoneId zone;
    private final Clock clock;

    public HomeService(RouteRepository routes, TripRepository trips, PromotionRepository promotions,
                       UserRepository users, AppProperties props, Clock clock) {
        this.routes = routes;
        this.trips = trips;
        this.promotions = promotions;
        this.users = users;
        this.zone = props.zone();
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public HomeResponse getHome(Long userId) {
        ZonedDateTime now = ZonedDateTime.now(clock.withZone(zone));

        Optional<User> user = userId == null ? Optional.empty() : users.findById(userId);
        String greetingName = user.map(User::givenName).orElse(null);

        List<Route> popular = routes.findByPopularRankNotNullOrderByPopularRankAsc();
        Map<Integer, List<Trip>> tripsByRoute = popular.isEmpty() ? Map.of()
                : trips.findActiveByRouteIds(popular.stream().map(Route::getId).toList()).stream()
                .collect(Collectors.groupingBy(t -> t.getRoute().getId()));

        List<PopularRouteResponse> popularRoutes = popular.stream()
                .filter(r -> tripsByRoute.containsKey(r.getId()))
                .map(r -> toPopularRoute(r, tripsByRoute.get(r.getId())))
                .toList();

        List<PromotionResponse> promos = promotions.findRunning(now.toInstant()).stream()
                .map(this::toPromotion)
                .toList();

        HomeResponse.SuggestedTrip suggested = popular.stream()
                .map(r -> tripsByRoute.getOrDefault(r.getId(), List.of()))
                .filter(list -> !list.isEmpty())
                .findFirst()
                .flatMap(list -> suggest(list, now))
                .orElse(null);

        HomeResponse.SearchDefaults defaults = suggested != null
                ? new HomeResponse.SearchDefaults(suggested.trip().origin(), suggested.trip().destination(),
                suggested.trip().departureDate())
                : new HomeResponse.SearchDefaults("TP. Hồ Chí Minh", "Đà Lạt", now.toLocalDate().plusDays(1));

        return new HomeResponse(user.isPresent(), greetingName, defaults, promos, popularRoutes, suggested);
    }

    private Optional<HomeResponse.SuggestedTrip> suggest(List<Trip> candidates, ZonedDateTime now) {
        LocalDateTime earliest = now.toLocalDateTime().plus(MIN_LEAD_TIME);
        record Option(Trip trip, LocalDateTime departure) { }

        return candidates.stream()
                .filter(t -> t.getSeatsLeft() > 0)
                .map(t -> {
                    LocalDateTime today = now.toLocalDate().atTime(t.getDepartTime());
                    return new Option(t, today.isBefore(earliest) ? today.plusDays(1) : today);
                })
                .min(Comparator.comparing(Option::departure)
                        .thenComparing(o -> o.trip().getRating(), Comparator.reverseOrder())
                        .thenComparingInt(o -> o.trip().getPrice()))
                .map(o -> {
                    String when = o.departure().toLocalDate().equals(now.toLocalDate())
                            ? "Khởi hành sớm nhất hôm nay" : "Khởi hành sớm nhất ngày mai";
                    String seats = o.trip().getSeatsLeft() <= 5
                            ? "Chỉ còn " + o.trip().getSeatsLeft() + " ghế"
                            : "Còn " + o.trip().getSeatsLeft() + " ghế";
                    return new HomeResponse.SuggestedTrip(when + " · " + seats,
                            toTrip(o.trip(), o.departure().toLocalDate()));
                });
    }

    static TripResponse toTrip(Trip t, LocalDate date) {
        int depart = t.getDepartTime().getHour() * 60 + t.getDepartTime().getMinute();
        int arriveTotal = depart + t.getDurationMinutes();
        return new TripResponse(
                t.getId(),
                t.getOperator().getName(),
                t.getBusName(),
                t.getBusType().name(),
                t.getRating().doubleValue(),
                date,
                hhmm(depart),
                hhmm(arriveTotal % (24 * 60)),
                arriveTotal / (24 * 60),
                depart,
                t.getDurationMinutes(),
                t.getPrice(),
                t.getSeatsLeft(),
                t.getTotalSeats(),
                t.getRoute().getOriginCity(),
                t.getRoute().getDestinationCity(),
                t.getPickupStation().getName(),
                t.getPickupStation().getAddress(),
                t.getDropoffStation().getName(),
                t.getDropoffStation().getAddress());
    }

    private PopularRouteResponse toPopularRoute(Route r, List<Trip> routeTrips) {
        int fromPrice = routeTrips.stream().mapToInt(Trip::getPrice).min().orElse(0);
        String first = routeTrips.stream().map(Trip::getDepartTime).min(Comparator.naturalOrder())
                .map(t -> hhmm(t.getHour() * 60 + t.getMinute())).orElse(null);
        return new PopularRouteResponse(r.getId(), r.getOriginCity(), r.getDestinationCity(), r.getDistanceKm(),
                fromPrice, routeTrips.size(), first, r.getImageKey());
    }

    private PromotionResponse toPromotion(Promotion p) {
        Route r = p.getRoute();
        return new PromotionResponse(p.getCode(), p.getBadge(), p.getTitle(), p.getDescription(), p.getNote(),
                p.getDiscountPercent(), p.getMaxDiscount(), p.getImageKey(),
                r == null ? null : r.getOriginCity(),
                r == null ? null : r.getDestinationCity(),
                p.getEndsAt().atZone(zone).toLocalDate());
    }

    private static String hhmm(int minutes) {
        return String.format("%02d:%02d", minutes / 60, minutes % 60);
    }
}
