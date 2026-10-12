package com.nexusmap.dto.admin;

import java.time.Instant;
import java.util.Map;

public record AdminOverviewDto(
    long totalRegisteredUsers,
    long currentlyActiveUsers,
    long activeZonesCount,
    long activePinsCount,
    long activeMeetupsCount,
    long totalMessagesCount,
    long messagesInLast24Hours,
    Instant timestamp,
    Map<String, String> metricDefinitions
) {}
