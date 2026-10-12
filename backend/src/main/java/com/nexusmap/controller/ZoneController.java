package com.nexusmap.controller;

import com.nexusmap.dto.CampusContainsResponse;
import com.nexusmap.dto.NearbyZoneResponseDto;
import com.nexusmap.dto.ZoneResponseDto;
import com.nexusmap.model.User;
import com.nexusmap.model.Zone;
import com.nexusmap.repository.UserRepository;
import com.nexusmap.repository.ZoneMembershipRepository;
import com.nexusmap.repository.ZoneRepository;
import com.nexusmap.service.SpatialEngineService;
import com.nexusmap.service.ZoneMembershipService;
import com.nexusmap.util.GeometryUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/zones")
@RequiredArgsConstructor
public class ZoneController {

    private final ZoneRepository zoneRepository;
    private final ZoneMembershipRepository zoneMembershipRepository;
    private final SpatialEngineService spatialEngineService;
    private final ZoneMembershipService zoneMembershipService;
    private final UserRepository userRepository;

    /**
     * GET /api/zones - Returns all active campus zones with boundaries and active user counts.
     */
    @GetMapping
    public ResponseEntity<List<ZoneResponseDto>> getAllZones() {
        List<Zone> zones = zoneRepository.findByActiveTrue();
        List<ZoneResponseDto> response = zones.stream().map(zone -> {
            long activeMembers = zoneMembershipRepository.countByZoneAndActiveTrue(zone);
            return ZoneResponseDto.fromEntity(zone, activeMembers);
        }).toList();

        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/zones/nearby - Finds active zones within a given radius in meters.
     */
    @GetMapping("/nearby")
    public ResponseEntity<List<NearbyZoneResponseDto>> getNearbyZones(
            @RequestParam double lat,
            @RequestParam double lng,
            @RequestParam(defaultValue = "1000") double radius
    ) {
        GeometryUtils.validateCoordinates(lat, lng);
        List<NearbyZoneResponseDto> nearby = spatialEngineService.findZonesNearby(lat, lng, radius);
        return ResponseEntity.ok(nearby);
    }

    /**
     * GET /api/zones/current - Gets current active zone for the authenticated user.
     */
    @GetMapping("/current")
    public ResponseEntity<ZoneResponseDto> getCurrentZone(@AuthenticationPrincipal UserDetails userDetails) {
        if (userDetails == null) {
            return ResponseEntity.status(401).build();
        }

        User user = userRepository.findByEmailIgnoreCase(userDetails.getUsername())
            .orElseThrow(() -> new IllegalArgumentException("User not found: " + userDetails.getUsername()));

        Optional<ZoneResponseDto> currentZoneOpt = zoneMembershipService.getCurrentActiveZone(user);
        return currentZoneOpt.map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.noContent().build());
    }
}
