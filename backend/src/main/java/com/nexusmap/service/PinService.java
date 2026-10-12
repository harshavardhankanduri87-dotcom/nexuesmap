package com.nexusmap.service;

import com.nexusmap.dto.*;
import com.nexusmap.exception.ResourceNotFoundException;
import com.nexusmap.model.*;
import com.nexusmap.repository.MeetupParticipantRepository;
import com.nexusmap.repository.MeetupRepository;
import com.nexusmap.repository.PinRepository;
import com.nexusmap.service.redis.RedisMessagePublisher;
import com.nexusmap.util.GeometryUtils;
import org.locationtech.jts.geom.Point;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Service
public class PinService {

    private static final Logger log = LoggerFactory.getLogger(PinService.class);

    private final PinRepository pinRepository;
    private final MeetupRepository meetupRepository;
    private final MeetupParticipantRepository participantRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final RedisMessagePublisher redisMessagePublisher;

    private static final int MAX_PINS_PER_10_MINUTES = 5;
    private static final double MAX_SEARCH_RADIUS_METERS = 50000.0; // 50 km limit
    private static final double MIN_SEARCH_RADIUS_METERS = 10.0;

    public PinService(
            PinRepository pinRepository,
            MeetupRepository meetupRepository,
            MeetupParticipantRepository participantRepository,
            @Lazy SimpMessagingTemplate messagingTemplate,
            RedisMessagePublisher redisMessagePublisher
    ) {
        this.pinRepository = pinRepository;
        this.meetupRepository = meetupRepository;
        this.participantRepository = participantRepository;
        this.messagingTemplate = messagingTemplate;
        this.redisMessagePublisher = redisMessagePublisher;
    }

    /**
     * Creates a new global pin and optional meetup.
     */
    @Transactional
    public PinResponseDto createPin(CreatePinRequest request, User creator) {
        GeometryUtils.validateCoordinates(request.getLatitude(), request.getLongitude());

        // Anti-spam rate limiting: max 5 pins per 10 minutes per creator
        Instant tenMinutesAgo = Instant.now().minus(Duration.ofMinutes(10));
        long recentPins = pinRepository.countByCreatorIdAndCreatedAtAfter(creator.getId(), tenMinutesAgo);
        if (recentPins >= MAX_PINS_PER_10_MINUTES) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                "Pin creation rate limit exceeded. You can create at most 5 pins per 10 minutes.");
        }

        // Calculate expiration timestamp
        Instant now = Instant.now();
        Instant expiresAt;
        if (request.getExpiresAt() != null) {
            if (request.getExpiresAt().isBefore(now)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Expiration timestamp must be in the future.");
            }
            if (request.getExpiresAt().isAfter(now.plus(Duration.ofDays(30)))) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Expiration cannot exceed 30 days into the future.");
            }
            expiresAt = request.getExpiresAt();
        } else {
            int durationHours = (request.getDurationHours() != null && request.getDurationHours() > 0)
                ? Math.min(request.getDurationHours(), 720)
                : 24;
            expiresAt = now.plus(Duration.ofHours(durationHours));
        }

        Point location = GeometryUtils.createPoint(request.getLatitude(), request.getLongitude());

        String sanitizedTitle = sanitizeHtml(request.getTitle().trim());
        String sanitizedDesc = sanitizeHtml(request.getDescription().trim());

        Pin pin = Pin.builder()
            .title(sanitizedTitle)
            .description(sanitizedDesc)
            .category(request.getCategory())
            .status(PinStatus.ACTIVE)
            .location(location)
            .latitude(request.getLatitude())
            .longitude(request.getLongitude())
            .creator(creator)
            .expiresAt(expiresAt)
            .build();

        Pin savedPin = pinRepository.save(pin);

        // If category is MEETUP or meetup details provided, provision Meetup entity
        if (request.getCategory() == PinCategory.MEETUP || request.getMeetupDetails() != null) {
            CreateMeetupDetailsDto meetupDetails = request.getMeetupDetails();
            Instant startTime = (meetupDetails != null && meetupDetails.getStartTime() != null)
                ? meetupDetails.getStartTime()
                : now.plus(Duration.ofHours(1));

            Instant endTime = meetupDetails != null ? meetupDetails.getEndTime() : null;
            Integer capacity = meetupDetails != null ? meetupDetails.getCapacity() : null;

            Meetup meetup = Meetup.builder()
                .pin(savedPin)
                .startTime(startTime)
                .endTime(endTime)
                .capacity(capacity)
                .active(true)
                .build();

            Meetup savedMeetup = meetupRepository.save(meetup);

            // Automatically register creator as first attendee
            MeetupParticipant participant = MeetupParticipant.builder()
                .meetup(savedMeetup)
                .user(creator)
                .build();
            participantRepository.save(participant);
            savedMeetup.getParticipants().add(participant);

            savedPin.setMeetup(savedMeetup);
        }

        PinResponseDto responseDto = PinResponseDto.fromEntity(savedPin, creator, 0.0);
        broadcastPinEvent("PIN_CREATED", savedPin.getId(), responseDto);

        log.info("Created Pin #{} ['{}'] by user '{}' at ({}, {})",
            savedPin.getId(), savedPin.getTitle(), creator.getEmail(), savedPin.getLatitude(), savedPin.getLongitude());

        return responseDto;
    }

    /**
     * Discovers active pins within search radius using PostGIS/spatial bounding box + Haversine distance.
     */
    @Transactional(readOnly = true)
    public PaginatedPinsResponse findNearbyPins(
            double latitude,
            double longitude,
            Double radiusMeters,
            PinCategory category,
            int page,
            int size,
            User currentUser
    ) {
        GeometryUtils.validateCoordinates(latitude, longitude);

        double radius = (radiusMeters == null || radiusMeters <= 0) ? 1000.0 : radiusMeters;
        radius = Math.min(Math.max(radius, MIN_SEARCH_RADIUS_METERS), MAX_SEARCH_RADIUS_METERS);

        int pageNum = Math.max(page, 0);
        int pageSize = Math.min(Math.max(size, 1), 100);

        // Bounding box in degrees
        double deltaLat = Math.toDegrees(radius / 6371008.8);
        double deltaLng = Math.toDegrees(radius / (6371008.8 * Math.max(0.01, Math.cos(Math.toRadians(latitude)))));

        double minLat = latitude - deltaLat;
        double maxLat = latitude + deltaLat;
        double minLng = longitude - deltaLng;
        double maxLng = longitude + deltaLng;

        Instant now = Instant.now();
        List<Pin> candidates = pinRepository.findActivePinsInBoundingBox(minLat, maxLat, minLng, maxLng, category, now);

        // Filter and calculate precise geodesic distance
        Map<Long, Double> distanceMap = new HashMap<>();
        List<Pin> withinRadius = new ArrayList<>();

        for (Pin pin : candidates) {
            if (pin.getStatus() != PinStatus.ACTIVE || pin.getExpiresAt().isBefore(now)) {
                continue;
            }
            double dist = GeometryUtils.calculateDistanceMeters(latitude, longitude, pin.getLatitude(), pin.getLongitude());
            if (dist <= radius) {
                distanceMap.put(pin.getId(), dist);
                withinRadius.add(pin);
            }
        }

        // Sort by ascending distance from query origin
        withinRadius.sort(Comparator.comparingDouble(p -> distanceMap.getOrDefault(p.getId(), 0.0)));

        int totalElements = withinRadius.size();
        int totalPages = (int) Math.ceil((double) totalElements / pageSize);
        int fromIndex = pageNum * pageSize;

        List<PinResponseDto> pagedResults = new ArrayList<>();
        if (fromIndex < totalElements) {
            int toIndex = Math.min(fromIndex + pageSize, totalElements);
            List<Pin> subList = withinRadius.subList(fromIndex, toIndex);
            for (Pin pin : subList) {
                pagedResults.add(PinResponseDto.fromEntity(pin, currentUser, distanceMap.get(pin.getId())));
            }
        }

        return PaginatedPinsResponse.builder()
            .pins(pagedResults)
            .pageNumber(pageNum)
            .pageSize(pageSize)
            .totalElements(totalElements)
            .totalPages(totalPages)
            .hasMore((pageNum + 1) < totalPages)
            .build();
    }

    /**
     * Retrieves pin details by ID.
     */
    @Transactional(readOnly = true)
    public PinResponseDto getPinById(Long id, User currentUser, Double userLat, Double userLng) {
        Pin pin = pinRepository.findDetailedByIdAndStatusNot(id, PinStatus.DELETED)
            .orElseThrow(() -> new ResourceNotFoundException("Pin", "id", id));

        Double distanceMeters = null;
        if (userLat != null && userLng != null) {
            try {
                GeometryUtils.validateCoordinates(userLat, userLng);
                distanceMeters = GeometryUtils.calculateDistanceMeters(userLat, userLng, pin.getLatitude(), pin.getLongitude());
            } catch (Exception ignored) {}
        }

        return PinResponseDto.fromEntity(pin, currentUser, distanceMeters);
    }

    /**
     * Updates an existing pin (only creator or ROLE_ADMIN).
     */
    @Transactional
    public PinResponseDto updatePin(Long id, UpdatePinRequest request, User currentUser) {
        Pin pin = pinRepository.findDetailedByIdAndStatusNot(id, PinStatus.DELETED)
            .orElseThrow(() -> new ResourceNotFoundException("Pin", "id", id));

        verifyOwnershipOrAdmin(pin, currentUser);

        if (request.getTitle() != null && !request.getTitle().isBlank()) {
            pin.setTitle(sanitizeHtml(request.getTitle().trim()));
        }
        if (request.getDescription() != null && !request.getDescription().isBlank()) {
            pin.setDescription(sanitizeHtml(request.getDescription().trim()));
        }
        if (request.getCategory() != null) {
            pin.setCategory(request.getCategory());
        }
        if (request.getExpiresAt() != null) {
            if (request.getExpiresAt().isBefore(Instant.now())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Expiration timestamp must be in the future.");
            }
            pin.setExpiresAt(request.getExpiresAt());
        }

        // Update meetup details if present
        if (request.getMeetupDetails() != null && pin.getMeetup() != null) {
            Meetup meetup = pin.getMeetup();
            if (request.getMeetupDetails().getStartTime() != null) {
                meetup.setStartTime(request.getMeetupDetails().getStartTime());
            }
            if (request.getMeetupDetails().getEndTime() != null) {
                meetup.setEndTime(request.getMeetupDetails().getEndTime());
            }
            if (request.getMeetupDetails().getCapacity() != null) {
                meetup.setCapacity(request.getMeetupDetails().getCapacity());
            }
            meetupRepository.save(meetup);
        }

        Pin updated = pinRepository.save(pin);
        PinResponseDto responseDto = PinResponseDto.fromEntity(updated, currentUser, null);
        broadcastPinEvent("PIN_UPDATED", updated.getId(), responseDto);

        return responseDto;
    }

    /**
     * Deletes a pin (only creator or ROLE_ADMIN).
     */
    @Transactional
    public void deletePin(Long id, User currentUser) {
        Pin pin = pinRepository.findDetailedByIdAndStatusNot(id, PinStatus.DELETED)
            .orElseThrow(() -> new ResourceNotFoundException("Pin", "id", id));

        verifyOwnershipOrAdmin(pin, currentUser);

        pin.setStatus(PinStatus.DELETED);
        if (pin.getMeetup() != null) {
            pin.getMeetup().setActive(false);
            meetupRepository.save(pin.getMeetup());
        }
        pinRepository.save(pin);

        broadcastPinEvent("PIN_DELETED", id, null);
        log.info("Pin #{} soft-deleted by user '{}'", id, currentUser.getEmail());
    }

    /**
     * Joins a meetup with capacity enforcement and duplicate prevention.
     */
    @Transactional
    public MeetupResponseDto joinMeetup(Long pinOrMeetupId, User currentUser) {
        Meetup meetup = meetupRepository.findDetailedByPinId(pinOrMeetupId)
            .or(() -> meetupRepository.findDetailedById(pinOrMeetupId))
            .orElseThrow(() -> new ResourceNotFoundException("Meetup", "id", pinOrMeetupId));

        if (!meetup.isActive() || meetup.getPin().getStatus() != PinStatus.ACTIVE || meetup.getPin().isExpired()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot join an inactive or expired meetup.");
        }

        // Prevent duplicate registration
        if (participantRepository.existsByMeetupIdAndUserId(meetup.getId(), currentUser.getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "You are already registered for this meetup.");
        }

        // Enforce participant capacity
        if (meetup.isFull()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Meetup is at full capacity (" + meetup.getCapacity() + " participants).");
        }

        MeetupParticipant participant = MeetupParticipant.builder()
            .meetup(meetup)
            .user(currentUser)
            .build();
        participantRepository.save(participant);
        meetup.getParticipants().add(participant);

        MeetupResponseDto dto = MeetupResponseDto.fromEntity(meetup, currentUser.getId());
        PinResponseDto pinDto = PinResponseDto.fromEntity(meetup.getPin(), currentUser, null);
        broadcastPinEvent("MEETUP_JOINED", meetup.getPin().getId(), pinDto);

        log.info("User '{}' joined meetup #{} (Pin #{})", currentUser.getEmail(), meetup.getId(), meetup.getPin().getId());
        return dto;
    }

    /**
     * Leaves a meetup.
     */
    @Transactional
    public MeetupResponseDto leaveMeetup(Long pinOrMeetupId, User currentUser) {
        Meetup meetup = meetupRepository.findDetailedByPinId(pinOrMeetupId)
            .or(() -> meetupRepository.findDetailedById(pinOrMeetupId))
            .orElseThrow(() -> new ResourceNotFoundException("Meetup", "id", pinOrMeetupId));

        MeetupParticipant participant = participantRepository.findByMeetupIdAndUserId(meetup.getId(), currentUser.getId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "You are not registered for this meetup."));

        participantRepository.delete(participant);
        meetup.getParticipants().remove(participant);

        MeetupResponseDto dto = MeetupResponseDto.fromEntity(meetup, currentUser.getId());
        PinResponseDto pinDto = PinResponseDto.fromEntity(meetup.getPin(), currentUser, null);
        broadcastPinEvent("MEETUP_LEFT", meetup.getPin().getId(), pinDto);

        log.info("User '{}' left meetup #{} (Pin #{})", currentUser.getEmail(), meetup.getId(), meetup.getPin().getId());
        return dto;
    }

    private void verifyOwnershipOrAdmin(Pin pin, User user) {
        if (user.getRole() == Role.ROLE_ADMIN) {
            return;
        }
        if (!pin.getCreator().getId().equals(user.getId())) {
            throw new AccessDeniedException("You do not have permission to modify or delete this pin.");
        }
    }

    private void broadcastPinEvent(String eventType, Long pinId, PinResponseDto pinDto) {
        PinEventDto event = PinEventDto.builder()
            .eventType(eventType)
            .pinId(pinId)
            .pin(pinDto)
            .timestamp(Instant.now())
            .build();

        // 1. Local STOMP WebSocket broadcast to /topic/pins
        try {
            messagingTemplate.convertAndSend("/topic/pins", event);
        } catch (Exception ex) {
            log.warn("Failed to broadcast pin event to local STOMP /topic/pins: {}", ex.getMessage());
        }

        // 2. Redis Pub/Sub cluster broadcast
        redisMessagePublisher.publishPinEvent(event);
    }

    private String sanitizeHtml(String input) {
        if (input == null) return null;
        return input.replace("&", "&amp;")
                    .replace("<", "&lt;")
                    .replace(">", "&gt;");
    }
}
