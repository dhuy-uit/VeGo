package com.vego.backend.trip;

import com.vego.backend.common.ApiException;
import com.vego.backend.home.dto.TripResponse;
import com.vego.backend.security.AuthUser;
import com.vego.backend.trip.dto.SeatMapResponse;
import com.vego.backend.trip.dto.StationResponse;
import com.vego.backend.trip.dto.SuggestionResponse;
import com.vego.backend.trip.dto.TripSearchResponse;
import com.vego.backend.trip.dto.TripStopsResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@RestController
@Tag(name = "Chuyến xe")
public class TripController {

    private final TripService trips;
    private final SuggestionService suggestions;
    private final SuggestionEvents events;

    public TripController(TripService trips, SuggestionService suggestions, SuggestionEvents events) {
        this.trips = trips;
        this.suggestions = suggestions;
        this.events = events;
    }

    /** Body của POST /api/v1/suggestions/click. */
    public record SuggestionClick(String tripId) { }

    @Operation(summary = "Tìm chuyến theo điểm đi, điểm đến, ngày đi (không cần đăng nhập)",
            description = "Bộ lọc tùy chọn: operator (nhiều giá trị), busType (LIMOUSINE | SLEEPER | SEATER), "
                    + "slot (0 = 00–06h, 1 = 06–12h, 2 = 12–18h, 3 = 18–24h), minPrice, maxPrice; "
                    + "sort = RECOMMENDED | DEPARTURE | PRICE | RATING")
    @GetMapping("/api/v1/trips/search")
    public TripSearchResponse search(
            @RequestParam String origin,
            @RequestParam String destination,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) List<String> operator,
            @RequestParam(required = false) List<String> busType,
            @RequestParam(required = false) List<Integer> slot,
            @RequestParam(required = false) Integer minPrice,
            @RequestParam(required = false) Integer maxPrice,
            @RequestParam(required = false) String sort) {
        if (origin.isBlank()) throw ApiException.field("VALIDATION_ERROR", "origin", "Vui lòng nhập điểm đi");
        if (destination.isBlank()) {
            throw ApiException.field("VALIDATION_ERROR", "destination", "Vui lòng nhập điểm đến");
        }
        return trips.search(origin, destination, date,
                new TripService.Filter(operator, busType, slot, minPrice, maxPrice, sort));
    }

    @Operation(summary = "Các thành phố có tuyến xe")
    @GetMapping("/api/v1/cities")
    public List<String> cities() {
        return trips.cities();
    }

    @Operation(summary = "Chi tiết một chuyến trong ngày (số ghế trống theo ngày)")
    @GetMapping("/api/v1/trips/{tripId}")
    public TripResponse get(@PathVariable String tripId,
                            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return trips.get(tripId, date);
    }

    @Operation(summary = "Sơ đồ ghế: các tầng, hàng ghế và ghế đã bán / đang có người giữ")
    @GetMapping("/api/v1/trips/{tripId}/seats")
    public SeatMapResponse seats(@PathVariable String tripId,
                                 @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return trips.seatMap(tripId, date);
    }

    @Operation(summary = "Điểm đón / điểm trả của chuyến")
    @GetMapping("/api/v1/trips/{tripId}/stops")
    public TripStopsResponse stops(@PathVariable String tripId) {
        return trips.stops(tripId);
    }

    @Operation(summary = "Danh sách bến xe (lọc theo thành phố nếu có)")
    @GetMapping("/api/v1/stations")
    public List<StationResponse> stations(@RequestParam(required = false) String city) {
        return trips.stations(city);
    }

    @Operation(summary = "Gợi ý chuyến \"Dành riêng cho bạn\" (đăng nhập thì dựa trên chuyến đã đi)")
    @GetMapping("/api/v1/suggestions")
    public ResponseEntity<SuggestionResponse> suggestions(
            @AuthenticationPrincipal AuthUser me,
            @Parameter(description = "Ngày đi muốn xem, mặc định ngày mai")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        Long userId = me == null ? null : me.id();
        return suggestions.suggest(userId, date)
                .map(s -> {
                    List<String> shown = new ArrayList<>();
                    shown.add(s.main().id());
                    s.more().forEach(t -> shown.add(t.id()));
                    events.impressions(userId, shown);
                    return ResponseEntity.ok(s);
                })
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @Operation(summary = "Ghi nhận người dùng bấm vào một chuyến được đề xuất (số liệu cho web admin)")
    @PostMapping("/api/v1/suggestions/click")
    public ResponseEntity<Void> suggestionClick(@AuthenticationPrincipal AuthUser me,
                                                @RequestBody SuggestionClick body) {
        if (body == null || !events.click(me == null ? null : me.id(), body.tripId())) {
            throw ApiException.notFound("TRIP_NOT_FOUND", "Không tìm thấy chuyến xe");
        }
        return ResponseEntity.noContent().build();
    }
}
