package com.vego.backend.home.dto;

public record PopularRouteResponse(
        int routeId,
        String origin,
        String destination,
        Integer distanceKm,
        int fromPrice,
        int tripsPerDay,
        String firstDeparture,
        String imageKey) {
}
