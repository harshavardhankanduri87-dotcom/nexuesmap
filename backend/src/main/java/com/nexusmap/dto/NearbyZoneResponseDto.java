package com.nexusmap.dto;

public record NearbyZoneResponseDto(
    ZoneResponseDto zone,
    double distanceMeters
) {
}
