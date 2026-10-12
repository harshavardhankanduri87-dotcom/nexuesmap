package com.nexusmap.dto.admin;

import java.util.List;

public record DensityHeatmapDto(
    int minThreshold,
    long totalObservedMembers,
    long privacyEligibleMembers,
    int suppressedAreasCount,
    List<ZoneDensityPointDto> densityPoints
) {
    public record ZoneDensityPointDto(
        Long zoneId,
        String zoneName,
        String zoneCode,
        double centerLat,
        double centerLng,
        List<List<Double>> boundaryCoordinates,
        long userCount,
        double intensity,
        String densityLevel
    ) {}
}
