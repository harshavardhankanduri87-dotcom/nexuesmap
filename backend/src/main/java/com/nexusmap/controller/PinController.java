package com.nexusmap.controller;

import com.nexusmap.dto.*;
import com.nexusmap.model.PinCategory;
import com.nexusmap.model.User;
import com.nexusmap.repository.UserRepository;
import com.nexusmap.service.PinService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class PinController {

    private final PinService pinService;
    private final UserRepository userRepository;

    /**
     * POST /api/pins — Create a new pin
     */
    @PostMapping("/api/pins")
    public ResponseEntity<PinResponseDto> createPin(
            @Valid @RequestBody CreatePinRequest request,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        if (userDetails == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        User user = resolveUser(userDetails);
        PinResponseDto response = pinService.createPin(request, user);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * GET /api/pins/nearby — Discover pins near a supplied geographic location
     */
    @GetMapping("/api/pins/nearby")
    public ResponseEntity<PaginatedPinsResponse> getNearbyPins(
            @RequestParam double lat,
            @RequestParam double lng,
            @RequestParam(required = false, defaultValue = "1000") Double radiusMeters,
            @RequestParam(required = false) PinCategory category,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        User user = userDetails != null ? resolveUserOrNull(userDetails) : null;
        PaginatedPinsResponse response = pinService.findNearbyPins(lat, lng, radiusMeters, category, page, size, user);
        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/pins/{id} — View pin details
     */
    @GetMapping("/api/pins/{id}")
    public ResponseEntity<PinResponseDto> getPin(
            @PathVariable Long id,
            @RequestParam(required = false) Double userLat,
            @RequestParam(required = false) Double userLng,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        User user = userDetails != null ? resolveUserOrNull(userDetails) : null;
        PinResponseDto response = pinService.getPinById(id, user, userLat, userLng);
        return ResponseEntity.ok(response);
    }

    /**
     * PATCH /api/pins/{id} — Edit an authorized pin
     */
    @PatchMapping("/api/pins/{id}")
    public ResponseEntity<PinResponseDto> updatePin(
            @PathVariable Long id,
            @Valid @RequestBody UpdatePinRequest request,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        if (userDetails == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        User user = resolveUser(userDetails);
        PinResponseDto response = pinService.updatePin(id, request, user);
        return ResponseEntity.ok(response);
    }

    /**
     * DELETE /api/pins/{id} — Delete an authorized pin
     */
    @DeleteMapping("/api/pins/{id}")
    public ResponseEntity<Void> deletePin(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        if (userDetails == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        User user = resolveUser(userDetails);
        pinService.deletePin(id, user);
        return ResponseEntity.noContent().build();
    }

    /**
     * POST /api/pins/{id}/join — Join a meetup associated with a pin
     */
    @PostMapping("/api/pins/{id}/join")
    public ResponseEntity<MeetupResponseDto> joinPinMeetup(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        if (userDetails == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        User user = resolveUser(userDetails);
        MeetupResponseDto response = pinService.joinMeetup(id, user);
        return ResponseEntity.ok(response);
    }

    /**
     * POST /api/pins/{id}/leave — Leave a meetup associated with a pin
     */
    @PostMapping("/api/pins/{id}/leave")
    public ResponseEntity<MeetupResponseDto> leavePinMeetup(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        if (userDetails == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        User user = resolveUser(userDetails);
        MeetupResponseDto response = pinService.leaveMeetup(id, user);
        return ResponseEntity.ok(response);
    }

    /**
     * POST /api/meetups/{id}/join — Join meetup by meetup ID
     */
    @PostMapping("/api/meetups/{id}/join")
    public ResponseEntity<MeetupResponseDto> joinMeetup(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        if (userDetails == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        User user = resolveUser(userDetails);
        MeetupResponseDto response = pinService.joinMeetup(id, user);
        return ResponseEntity.ok(response);
    }

    /**
     * POST /api/meetups/{id}/leave — Leave meetup by meetup ID
     */
    @PostMapping("/api/meetups/{id}/leave")
    public ResponseEntity<MeetupResponseDto> leaveMeetup(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        if (userDetails == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        User user = resolveUser(userDetails);
        MeetupResponseDto response = pinService.leaveMeetup(id, user);
        return ResponseEntity.ok(response);
    }

    private User resolveUser(UserDetails userDetails) {
        return userRepository.findByEmailIgnoreCase(userDetails.getUsername())
            .orElseThrow(() -> new UsernameNotFoundException("User not found: " + userDetails.getUsername()));
    }

    private User resolveUserOrNull(UserDetails userDetails) {
        return userRepository.findByEmailIgnoreCase(userDetails.getUsername()).orElse(null);
    }
}
