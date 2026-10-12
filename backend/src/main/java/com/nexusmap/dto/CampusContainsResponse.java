package com.nexusmap.dto;

import java.time.Instant;

public record CampusContainsResponse(
    boolean insideCampus,
    double latitude,
    double longitude,
    int containingZoneCount,
    Instant timestamp
) {
}
