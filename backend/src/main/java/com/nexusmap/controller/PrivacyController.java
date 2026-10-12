package com.nexusmap.controller;

import com.nexusmap.dto.PeerLocationDto;
import com.nexusmap.dto.PrivacySettingsDto;
import com.nexusmap.dto.UpdatePrivacySettingsRequest;
import com.nexusmap.model.User;
import com.nexusmap.model.UserLocation;
import com.nexusmap.model.Zone;
import com.nexusmap.model.ZoneMembership;
import com.nexusmap.repository.UserLocationRepository;
import com.nexusmap.repository.UserRepository;
import com.nexusmap.repository.ZoneMembershipRepository;
import com.nexusmap.repository.ZoneRepository;
import com.nexusmap.service.LocationPrivacyService;
import com.nexusmap.service.PresenceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Controller handling user privacy settings and peer location queries.
 *
 * Security Requirements:
 * - Current user is derived strictly from authenticated principal.
 * - Modifying another user's privacy settings is strictly impossible.
 * - Coordinates returned to peers are server-side fuzzed with anti-averaging protection.
 * - Ghost Mode or non-sharing users are completely omitted from peer locations.
 */
@RestController
@RequestMapping("/api/privacy")
@RequiredArgsConstructor
public class PrivacyController {

    private static final Logger log = LoggerFactory.getLogger(PrivacyController.class);

    private final UserRepository userRepository;
    private final UserLocationRepository userLocationRepository;
    private final ZoneRepository zoneRepository;
    private final ZoneMembershipRepository zoneMembershipRepository;
    private final LocationPrivacyService locationPrivacyService;
    private final PresenceService presenceService;

    /**
     * GET /api/privacy/settings - Retrieve privacy settings for authenticated user.
     */
    @GetMapping("/settings")
    public ResponseEntity<PrivacySettingsDto> getPrivacySettings(
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        if (userDetails == null) {
            return ResponseEntity.status(401).build();
        }

        User user = getUser(userDetails);
        return ResponseEntity.ok(PrivacySettingsDto.fromUser(user));
    }

    /**
     * PUT /api/privacy/settings - Update privacy settings for authenticated user.
     */
    @PutMapping("/settings")
    @Transactional
    public ResponseEntity<PrivacySettingsDto> updatePrivacySettings(
            @Valid @RequestBody UpdatePrivacySettingsRequest request,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        if (userDetails == null) {
            return ResponseEntity.status(401).build();
        }

        User user = getUser(userDetails);
        boolean ghostModeChanged = false;

        if (request.ghostMode() != null && request.ghostMode() != user.isGhostMode()) {
            user.setGhostMode(request.ghostMode());
            ghostModeChanged = true;
        }

        if (request.locationSharingEnabled() != null) {
            user.setLocationSharingEnabled(request.locationSharingEnabled());
        }

        if (request.fuzzingRadiusMeters() != null) {
            user.setFuzzingRadiusMeters(request.fuzzingRadiusMeters());
        }

        User saved = userRepository.save(user);

        if (ghostModeChanged) {
            presenceService.handleGhostModeToggled(saved);
        }

        log.debug("Updated privacy settings for user {}: ghostMode={}, sharing={}",
            saved.getEmail(), saved.isGhostMode(), saved.isLocationSharingEnabled());

        return ResponseEntity.ok(PrivacySettingsDto.fromUser(saved));
    }

    /**
     * GET /api/privacy/peers - Discover nearby peer locations in active zone.
     * Raw coordinates are never exposed; fuzzed coordinates with anti-averaging are returned.
     * Users in Ghost Mode or with location sharing disabled are completely omitted.
     */
    @GetMapping("/peers")
    @Transactional(readOnly = true)
    public ResponseEntity<List<PeerLocationDto>> getNearbyPeers(
            @RequestParam(required = false) Long zoneId,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        if (userDetails == null) {
            return ResponseEntity.status(401).build();
        }

        User currentUser = getUser(userDetails);
        Zone targetZone = null;

        if (zoneId != null) {
            targetZone = zoneRepository.findById(zoneId).orElse(null);
        } else {
            Optional<ZoneMembership> activeOpt = zoneMembershipRepository.findByUserAndActiveTrue(currentUser);
            if (activeOpt.isPresent()) {
                targetZone = activeOpt.get().getZone();
            }
        }

        if (targetZone == null) {
            return ResponseEntity.ok(List.of());
        }

        // Active peer threshold: last 15 minutes
        Instant cutoff = Instant.now().minus(Duration.ofMinutes(15));
        List<ZoneMembership> memberships = zoneMembershipRepository.findAllByZoneAndActiveTrue(targetZone);
        List<PeerLocationDto> peerLocations = new ArrayList<>();

        for (ZoneMembership membership : memberships) {
            User peer = membership.getUser();

            // Privacy Filter: omit ghost mode users and non-sharing users
            if (!locationPrivacyService.isLocationSharingPermitted(peer)) {
                continue;
            }

            Optional<UserLocation> latestLocOpt = userLocationRepository.findFirstByUserOrderByRecordedAtDesc(peer);
            if (latestLocOpt.isEmpty()) {
                continue;
            }

            UserLocation loc = latestLocOpt.get();
            if (loc.getRecordedAt().isBefore(cutoff)) {
                continue;
            }

            // Fuzz coordinates before returning
            Optional<PeerLocationDto> peerDtoOpt = locationPrivacyService.createFuzzedPeerLocation(
                peer,
                loc.getLatitude(),
                loc.getLongitude(),
                targetZone.getId(),
                targetZone.getName(),
                loc.getRecordedAt()
            );

            peerDtoOpt.ifPresent(peerLocations::add);
        }

        return ResponseEntity.ok(peerLocations);
    }

    private User getUser(UserDetails userDetails) {
        return userRepository.findByEmailIgnoreCase(userDetails.getUsername())
            .orElseThrow(() -> new UsernameNotFoundException("User not found: " + userDetails.getUsername()));
    }
}
