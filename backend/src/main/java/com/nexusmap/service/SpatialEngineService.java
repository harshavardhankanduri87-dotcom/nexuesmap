package com.nexusmap.service;

import com.nexusmap.dto.NearbyZoneResponseDto;
import com.nexusmap.dto.ZoneResponseDto;
import com.nexusmap.model.User;
import com.nexusmap.model.Zone;
import com.nexusmap.model.ZoneType;
import com.nexusmap.repository.UserRepository;
import com.nexusmap.repository.ZoneMembershipRepository;
import com.nexusmap.repository.ZoneRepository;
import com.nexusmap.util.GeometryUtils;
import lombok.RequiredArgsConstructor;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Point;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SpatialEngineService {

    private static final Logger log = LoggerFactory.getLogger(SpatialEngineService.class);

    private final ZoneRepository zoneRepository;
    private final ZoneMembershipRepository zoneMembershipRepository;
    private final UserRepository userRepository;

    /**
     * Determines whether a point lies inside any active campus boundary.
     */
    @Transactional(readOnly = true)
    public boolean isPointInsideCampus(double latitude, double longitude) {
        GeometryUtils.validateCoordinates(latitude, longitude);
        Point point = GeometryUtils.createPoint(latitude, longitude);

        List<Zone> activeZones = zoneRepository.findByActiveTrue();
        
        // Check campus perimeter zone first if exists
        for (Zone zone : activeZones) {
            if (zone.getZoneType() == ZoneType.CAMPUS_PERIMETER && zone.getBoundary().covers(point)) {
                return true;
            }
        }

        // Otherwise check if contained in any active zone
        for (Zone zone : activeZones) {
            if (zone.getBoundary().covers(point)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Finds all active zones containing a user's location (Point-in-Polygon).
     * Handles overlapping zones by sorting specific facility zones ahead of perimeters.
     */
    @Transactional(readOnly = true)
    public List<Zone> findZonesContainingPoint(double latitude, double longitude) {
        GeometryUtils.validateCoordinates(latitude, longitude);
        Point point = GeometryUtils.createPoint(latitude, longitude);

        List<Zone> activeZones = zoneRepository.findByActiveTrue();
        List<Zone> containing = new ArrayList<>();

        for (Zone zone : activeZones) {
            // JTS covers() includes boundaries and interiors
            if (zone.getBoundary().covers(point)) {
                containing.add(zone);
            }
        }

        // Sort: specific facility zones first, campus perimeter last
        containing.sort((z1, z2) -> {
            if (z1.getZoneType() == ZoneType.CAMPUS_PERIMETER) return 1;
            if (z2.getZoneType() == ZoneType.CAMPUS_PERIMETER) return -1;
            return Double.compare(z1.getBoundary().getArea(), z2.getBoundary().getArea());
        });

        return containing;
    }

    /**
     * Finds active zones within a configurable distance in meters.
     */
    @Transactional(readOnly = true)
    public List<NearbyZoneResponseDto> findZonesNearby(double latitude, double longitude, double radiusMeters) {
        GeometryUtils.validateCoordinates(latitude, longitude);
        if (radiusMeters < 0) {
            throw new IllegalArgumentException("Radius must be non-negative.");
        }

        List<Zone> activeZones = zoneRepository.findByActiveTrue();
        List<NearbyZoneResponseDto> nearbyList = new ArrayList<>();

        for (Zone zone : activeZones) {
            double distanceMeters = GeometryUtils.calculateDistanceMeters(
                latitude, longitude, zone.getCenterLat(), zone.getCenterLng()
            );

            // If center or boundary is within radius
            if (distanceMeters <= radiusMeters) {
                long activeMembers = zoneMembershipRepository.countByZoneAndActiveTrue(zone);
                ZoneResponseDto dto = ZoneResponseDto.fromEntity(zone, activeMembers);
                nearbyList.add(new NearbyZoneResponseDto(dto, distanceMeters));
            }
        }

        nearbyList.sort(Comparator.comparingDouble(NearbyZoneResponseDto::distanceMeters));
        return nearbyList;
    }

    private final LocationPrivacyService locationPrivacyService;

    /**
     * Obfuscates coordinates for Ghost Mode using server-side Anti-Averaging Gaussian noise (~100m).
     */
    public Coordinate obfuscateGhostCoordinates(double latitude, double longitude) {
        return locationPrivacyService.computeAntiAveragingFuzz(0L, latitude, longitude, 100);
    }
}
