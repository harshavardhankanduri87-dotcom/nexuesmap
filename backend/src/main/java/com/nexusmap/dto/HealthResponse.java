package com.nexusmap.dto;

import java.time.Instant;
import java.util.Map;

public record HealthResponse(
    String status,
    String service,
    String version,
    Instant timestamp,
    String environment,
    Map<String, String> subsystems
) {
}
