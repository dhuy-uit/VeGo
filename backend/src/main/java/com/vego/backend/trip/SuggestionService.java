package com.vego.backend.trip;

import com.vego.backend.booking.Booking;
import com.vego.backend.booking.BookingRepository;
import com.vego.backend.config.AppProperties;
import com.vego.backend.home.HomeService;
import com.vego.backend.home.Route;
import com.vego.backend.home.RouteRepository;
import com.vego.backend.home.Trip;
import com.vego.backend.home.TripRepository;
import com.vego.backend.trip.dto.SuggestionResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * "Dành riêng cho bạn": dựa trên chuyến đã đi gần nhất — cùng tuyến, cùng nhà xe, cùng buổi khởi hành,
 * giờ gần nhất lên trước. Chưa có lịch sử: các chuyến đánh giá cao của tuyến phổ biến nhất.
 */
@Service
public class SuggestionService {

    private static final int MAX_MORE = 2;

    private final BookingRepository bookings;
    private final TripRepository trips;
    private final RouteRepository routes;
    private final SeatInventory seats;
    private final ZoneId zone;
    private final Clock clock;

    public SuggestionService(BookingRepository bookings, TripRepository trips, RouteRepository routes,
                             SeatInventory seats, AppProperties props, Clock clock) {
        this.bookings = bookings;
        this.trips = trips;
        this.routes = routes;
        this.seats = seats;
        this.zone = props.zone();
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Optional<SuggestionResponse> suggest(Long userId, LocalDate requestedDate) {
        Instant now = clock.instant();
        LocalDate today = LocalDate.now(clock.withZone(zone));
        LocalDate date = requestedDate == null || requestedDate.isBefore(today) ? today.plusDays(1) : requestedDate;
        Instant earliest = now.plus(TripService.MIN_LEAD_TIME);

        Booking last = userId == null ? null : bookings.findTicketsOfUser(userId).stream()
                .filter(b -> b.getStatus() == Booking.Status.PAID)
                .filter(b -> TripTimes.departure(b.getTrip(), b.getTravelDate(), zone).isBefore(now))
                .max(Comparator.comparing(b -> TripTimes.departure(b.getTrip(), b.getTravelDate(), zone)))
                .orElse(null);

        List<Trip> candidates = new ArrayList<>();
        if (last != null) {
            Trip base = last.getTrip();
            int target = TripTimes.departMinutes(base);
            trips.findActiveByRouteIds(List.of(base.getRoute().getId())).stream()
                    .filter(t -> t.getOperator().getId().equals(base.getOperator().getId()))
                    .filter(t -> TripTimes.dayPart(TripTimes.departMinutes(t)) == TripTimes.dayPart(target))
                    .filter(t -> TripTimes.departure(t, date, zone).isAfter(earliest))
                    .sorted(Comparator.comparingInt(t -> Math.abs(TripTimes.departMinutes(t) - target)))
                    .forEach(candidates::add);
        }
        if (candidates.isEmpty()) {
            last = null;
            List<Route> popular = routes.findByPopularRankNotNullOrderByPopularRankAsc();
            if (!popular.isEmpty()) {
                trips.findActiveByRouteIds(List.of(popular.get(0).getId())).stream()
                        .filter(t -> TripTimes.departure(t, date, zone).isAfter(earliest))
                        .sorted(Comparator.comparing(Trip::getRating).reversed()
                                .thenComparingInt(TripTimes::departMinutes))
                        .forEach(candidates::add);
            }
        }
        if (candidates.isEmpty()) return Optional.empty();

        Map<String, Integer> left = seats.seatsLeft(candidates, date);
        List<Trip> withSeats = candidates.stream().filter(t -> left.getOrDefault(t.getId(), 0) > 0).toList();
        if (!withSeats.isEmpty()) candidates = withSeats;

        Trip main = candidates.get(0);
        List<com.vego.backend.home.dto.TripResponse> more = candidates.stream().skip(1).limit(MAX_MORE)
                .map(t -> HomeService.toTrip(t, date, left.getOrDefault(t.getId(), 0))).toList();

        SuggestionResponse.BasedOn basedOn = null;
        if (last != null) {
            Trip b = last.getTrip();
            int minutes = TripTimes.departMinutes(b);
            basedOn = new SuggestionResponse.BasedOn(last.getCode(), b.getRoute().getOriginCity(),
                    b.getRoute().getDestinationCity(), last.getTravelDate(), b.getOperator().getName(), minutes,
                    TripTimes.dayPartName(minutes));
        }
        return Optional.of(new SuggestionResponse(basedOn, date,
                HomeService.toTrip(main, date, left.getOrDefault(main.getId(), 0)), more));
    }
}
