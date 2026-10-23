package com.vego.backend.trip.dto;

import java.util.List;

/** Điểm đón (ở thành phố đi) và điểm trả (ở thành phố đến) của một chuyến; bến chính của chuyến đứng đầu. */
public record TripStopsResponse(String tripId, List<StationResponse> pickups, List<StationResponse> dropoffs) {
}
