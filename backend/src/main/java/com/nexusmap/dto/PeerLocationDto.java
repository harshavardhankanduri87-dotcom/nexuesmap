package com.nexusmap.dto;

import java.time.Instant;

public record PeerLocationDto(
    Long userId,
    String displayName,
    Double latitude,
    Double longitude,
    int fuzzingRadiusMeters,
    Long zoneId,
    String zoneName,
    Instant lastSeenAt
) {}
