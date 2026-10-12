package com.nexusmap.service;

import com.nexusmap.dto.LocationUpdateResponse;
import com.nexusmap.dto.ZoneResponseDto;
import com.nexusmap.model.*;
import com.nexusmap.repository.UserLocationRepository;
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

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ZoneMembershipService {

    private static final Logger log = LoggerFactory.getLogger(ZoneMembershipService.class);

    private final SpatialEngineService spatialEngineService;
    private final ZoneRepository zoneRepository;
    private final ZoneMembershipRepository zoneMembershipRepository;
    private final UserLocationRepository userLocationRepository;

    private static final double THROTTLE_DISTANCE_METERS = 3.0;
    private static final long THROTTLE_INTERVAL_MILLIS = 3000;

    /**
     * Processes incoming user GPS coordinate update, detects zone transitions,
     * updates memberships, and applies privacy controls.
     */
    @Transactional
    public LocationUpdateResponse processLocationUpdate(User user, double latitude, double longitude, Double accuracy) {
        GeometryUtils.validateCoordinates(latitude, longitude);

        Instant now = Instant.now();
        Point point = GeometryUtils.createPoint(latitude, longitude);

        // Check update throttling to prevent redundant DB writes when position has not meaningfully changed
        Optional<UserLocation> lastLocationOpt = userLocationRepository.findFirstByUserOrderByRecordedAtDesc(user);
        if (lastLocationOpt.isPresent()) {
            UserLocation lastLoc = lastLocationOpt.get();
            double distance = GeometryUtils.calculateDistanceMeters(
                latitude, longitude, lastLoc.getLatitude(), lastLoc.getLongitude()
            );
            long timeDelta = Duration.between(lastLoc.getRecordedAt(), now).toMillis();

            if (distance < THROTTLE_DISTANCE_METERS && timeDelta < THROTTLE_INTERVAL_MILLIS) {
                // Return maintained state quickly without heavy writes
                Optional<ZoneMembership> currentActive = zoneMembershipRepository.findByUserAndActiveTrue(user);
                ZoneResponseDto activeZoneDto = currentActive.map(m -> 
                    ZoneResponseDto.fromEntity(m.getZone(), zoneMembershipRepository.countByZoneAndActiveTrue(m.getZone()))
                ).orElse(null);

                return buildResponse(TransitionType.MAINTAINED, activeZoneDto, null, latitude, longitude, user.isGhostMode(), now);
            }
        }

        // Record UserLocation
        UserLocation newLocation = UserLocation.builder()
            .user(user)
            .location(point)
            .latitude(latitude)
            .longitude(longitude)
            .accuracy(accuracy)
            .build();
        userLocationRepository.save(newLocation);

        // Evaluate containing zones
        List<Zone> containingZones = spatialEngineService.findZonesContainingPoint(latitude, longitude);
        Zone targetZone = containingZones.isEmpty() ? null : containingZones.get(0);

        Optional<ZoneMembership> activeMembershipOpt = zoneMembershipRepository.findByUserAndActiveTrue(user);

        TransitionType transitionType;
        Zone previousZone = null;
        Zone currentZone = targetZone;

        if (targetZone != null) {
            if (activeMembershipOpt.isEmpty()) {
                // Case 1: Entering a zone from outside
                ZoneMembership newMembership = ZoneMembership.builder()
                    .user(user)
                    .zone(targetZone)
                    .active(true)
                    .build();
                zoneMembershipRepository.save(newMembership);
                transitionType = TransitionType.ENTERED;
            } else {
                ZoneMembership currentMembership = activeMembershipOpt.get();
                if (currentMembership.getZone().getId().equals(targetZone.getId())) {
                    // Case 2: Maintained position in same zone
                    currentMembership.setLastSeenAt(now);
                    zoneMembershipRepository.save(currentMembership);
                    transitionType = TransitionType.MAINTAINED;
                } else {
                    // Case 3: Transitioned from Zone A to Zone B
                    previousZone = currentMembership.getZone();
                    currentMembership.setActive(false);
                    currentMembership.setExitedAt(now);
                    zoneMembershipRepository.save(currentMembership);

                    ZoneMembership newMembership = ZoneMembership.builder()
                        .user(user)
                        .zone(targetZone)
                        .active(true)
                        .build();
                    zoneMembershipRepository.save(newMembership);
                    transitionType = TransitionType.ENTERED;
                }
            }
        } else {
            if (activeMembershipOpt.isPresent()) {
                // Case 4: Exited all zones
                ZoneMembership currentMembership = activeMembershipOpt.get();
                previousZone = currentMembership.getZone();
                currentMembership.setActive(false);
                currentMembership.setExitedAt(now);
                zoneMembershipRepository.save(currentMembership);
                transitionType = TransitionType.EXITED;
            } else {
                // Case 5: Outside all zones and was already outside
                transitionType = TransitionType.OUTSIDE_CAMPUS;
            }
        }

        ZoneResponseDto currentZoneDto = currentZone != null ?
            ZoneResponseDto.fromEntity(currentZone, zoneMembershipRepository.countByZoneAndActiveTrue(currentZone)) : null;

        ZoneResponseDto previousZoneDto = previousZone != null ?
            ZoneResponseDto.fromEntity(previousZone, zoneMembershipRepository.countByZoneAndActiveTrue(previousZone)) : null;

        return buildResponse(transitionType, currentZoneDto, previousZoneDto, latitude, longitude, user.isGhostMode(), now);
    }

    /**
     * Gets user's current active zone if present.
     */
    @Transactional(readOnly = true)
    public Optional<ZoneResponseDto> getCurrentActiveZone(User user) {
        return zoneMembershipRepository.findByUserAndActiveTrue(user)
            .map(m -> ZoneResponseDto.fromEntity(m.getZone(), zoneMembershipRepository.countByZoneAndActiveTrue(m.getZone())));
    }

    private LocationUpdateResponse buildResponse(
            TransitionType transitionType,
            ZoneResponseDto currentZone,
            ZoneResponseDto previousZone,
            double lat,
            double lng,
            boolean ghostMode,
            Instant timestamp
    ) {
        Double respLat = lat;
        Double respLng = lng;

        if (ghostMode) {
            Coordinate fuzzed = spatialEngineService.obfuscateGhostCoordinates(lat, lng);
            respLat = fuzzed.y;
            respLng = fuzzed.x;
        }

        return new LocationUpdateResponse(
            transitionType,
            currentZone,
            previousZone,
            respLat,
            respLng,
            ghostMode,
            timestamp
        );
    }
}
