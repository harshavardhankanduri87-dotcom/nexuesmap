package com.nexusmap.dto;

import com.nexusmap.model.TransitionType;

import java.time.Instant;

public record LocationUpdateResponse(
    TransitionType transitionType,
    ZoneResponseDto currentZone,
    ZoneResponseDto previousZone,
    Double latitude,
    Double longitude,
    boolean ghostModeActive,
    Instant timestamp
) {
}
