package com.nexusmap.dto.admin;

import java.time.Instant;
import java.util.List;

public record ActivityTrendDto(
    String interval,
    Instant rangeStart,
    Instant rangeEnd,
    long totalMessages,
    long totalLocationUpdates,
    long totalPinsCreated,
    List<ActivityBucketDto> buckets
) {
    public record ActivityBucketDto(
        String label,
        Instant bucketStart,
        Instant bucketEnd,
        long messageCount,
        long locationUpdateCount,
        long pinCreationCount
    ) {}
}
