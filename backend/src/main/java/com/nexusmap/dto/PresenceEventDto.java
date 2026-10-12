package com.nexusmap.dto;

import java.time.Instant;

public record PresenceEventDto(
    Long userId,
    String userName,
    Long zoneId,
    String status,
    boolean ghostMode,
    Instant timestamp
) {
}
