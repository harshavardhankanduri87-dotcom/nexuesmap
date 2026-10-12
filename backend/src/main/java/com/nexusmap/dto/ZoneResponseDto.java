package com.nexusmap.dto;

import com.nexusmap.model.Zone;
import com.nexusmap.model.ZoneType;
import com.nexusmap.util.GeometryUtils;

import java.util.List;

public record ZoneResponseDto(
    Long id,
    String name,
    String code,
    String description,
    ZoneType zoneType,
    boolean active,
    Double centerLat,
    Double centerLng,
    String color,
    long activeUsers,
    List<List<Double>> leafletCoordinates,
    List<List<Double>> geoJsonCoordinates
) {
    public static ZoneResponseDto fromEntity(Zone zone, long activeUsers) {
        return new ZoneResponseDto(
            zone.getId(),
            zone.getName(),
            zone.getCode(),
            zone.getDescription(),
            zone.getZoneType(),
            zone.isActive(),
            zone.getCenterLat(),
            zone.getCenterLng(),
            zone.getColor(),
            activeUsers,
            GeometryUtils.toLeafletCoordinates(zone.getBoundary()),
            GeometryUtils.toGeoJsonCoordinates(zone.getBoundary())
        );
    }
}
