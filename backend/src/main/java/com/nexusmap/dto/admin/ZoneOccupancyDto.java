package com.nexusmap.dto.admin;

import java.util.List;

public record ZoneOccupancyDto(
    Long zoneId,
    String zoneName,
    String zoneCode,
    String zoneType,
    long activeMembers,
    long privacyEligibleMembers,
    Double centerLat,
    Double centerLng,
    List<List<Double>> leafletCoordinates
) {}
