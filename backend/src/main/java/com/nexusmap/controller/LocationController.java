package com.nexusmap.controller;

import com.nexusmap.dto.LocationUpdateRequest;
import com.nexusmap.dto.LocationUpdateResponse;
import com.nexusmap.model.User;
import com.nexusmap.repository.UserRepository;
import com.nexusmap.service.ZoneMembershipService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/location")
@RequiredArgsConstructor
public class LocationController {

    private final ZoneMembershipService zoneMembershipService;
    private final UserRepository userRepository;

    /**
     * POST /api/location/update - Authenticated GPS update processing with zone transition detection.
     * Identity is securely derived from the authenticated principal.
     */
    @PostMapping("/update")
    public ResponseEntity<LocationUpdateResponse> updateLocation(
            @Valid @RequestBody LocationUpdateRequest request,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        if (userDetails == null) {
            return ResponseEntity.status(401).build();
        }

        User user = userRepository.findByEmailIgnoreCase(userDetails.getUsername())
            .orElseThrow(() -> new UsernameNotFoundException("User not found: " + userDetails.getUsername()));

        LocationUpdateResponse response = zoneMembershipService.processLocationUpdate(
            user,
            request.latitude(),
            request.longitude(),
            request.accuracy()
        );

        return ResponseEntity.ok(response);
    }
}
