package com.nexusmap.service;

import com.nexusmap.dto.PresenceEventDto;
import com.nexusmap.model.User;
import com.nexusmap.model.Zone;
import com.nexusmap.model.ZoneMembership;
import com.nexusmap.repository.UserRepository;
import com.nexusmap.repository.ZoneMembershipRepository;
import com.nexusmap.repository.ZoneRepository;
import com.nexusmap.service.redis.RedisMessagePublisher;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class PresenceService {

    private static final Logger log = LoggerFactory.getLogger(PresenceService.class);

    private final SimpMessagingTemplate messagingTemplate;
    private final RedisMessagePublisher redisMessagePublisher;
    private final ZoneMembershipRepository zoneMembershipRepository;
    private final UserRepository userRepository;

    public PresenceService(
            @org.springframework.context.annotation.Lazy SimpMessagingTemplate messagingTemplate,
            RedisMessagePublisher redisMessagePublisher,
            ZoneMembershipRepository zoneMembershipRepository,
            UserRepository userRepository
    ) {
        this.messagingTemplate = messagingTemplate;
        this.redisMessagePublisher = redisMessagePublisher;
        this.zoneMembershipRepository = zoneMembershipRepository;
        this.userRepository = userRepository;
    }

    // Track active sessions per user (handling multiple tabs)
    private final Map<String, Set<String>> userSessions = new ConcurrentHashMap<>();
    // Track session to user email
    private final Map<String, String> sessionUser = new ConcurrentHashMap<>();
    // Track session to subscribed zones
    private final Map<String, Set<Long>> sessionZones = new ConcurrentHashMap<>();

    /**
     * Handles new WebSocket session connection.
     */
    public void registerSession(String sessionId, String userEmail) {
        sessionUser.put(sessionId, userEmail);
        userSessions.computeIfAbsent(userEmail, k -> ConcurrentHashMap.newKeySet()).add(sessionId);
        sessionZones.put(sessionId, ConcurrentHashMap.newKeySet());
        log.debug("Registered session {} for user {}", sessionId, userEmail);
    }

    /**
     * Registers a session subscription to a specific zone channel.
     */
    public void joinZonePresence(String sessionId, Long zoneId) {
        String email = sessionUser.get(sessionId);
        if (email == null) return;

        Set<Long> zones = sessionZones.get(sessionId);
        if (zones != null) {
            zones.add(zoneId);
        }

        userRepository.findByEmailIgnoreCase(email).ifPresent(user -> {
            broadcastPresenceEvent(zoneId, user, "JOINED");
        });
    }

    /**
     * Removes session subscription from a zone channel.
     */
    public void leaveZonePresence(String sessionId, Long zoneId) {
        String email = sessionUser.get(sessionId);
        if (email == null) return;

        Set<Long> zones = sessionZones.get(sessionId);
        if (zones != null) {
            zones.remove(zoneId);
        }

        userRepository.findByEmailIgnoreCase(email).ifPresent(user -> {
            broadcastPresenceEvent(zoneId, user, "LEFT");
        });
    }

    /**
     * Handles WebSocket session disconnect (e.g. tab closed or network dropped).
     */
    public void removeSession(String sessionId) {
        String email = sessionUser.remove(sessionId);
        Set<Long> zones = sessionZones.remove(sessionId);

        if (email != null) {
            Set<String> sessions = userSessions.get(email);
            if (sessions != null) {
                sessions.remove(sessionId);
                if (sessions.isEmpty()) {
                    userSessions.remove(email);
                    // User has no remaining active tabs, broadcast leave events
                    if (zones != null) {
                        userRepository.findByEmailIgnoreCase(email).ifPresent(user -> {
                            for (Long zoneId : zones) {
                                broadcastPresenceEvent(zoneId, user, "OFFLINE");
                            }
                        });
                    }
                }
            }
        }
    }

    /**
     * Checks if a user has any active WebSocket sessions.
     */
    public boolean isUserOnline(String email) {
        Set<String> sessions = userSessions.get(email);
        return sessions != null && !sessions.isEmpty();
    }

    /**
     * Returns count of active online connections for a user.
     */
    public int getActiveSessionCount(String email) {
        Set<String> sessions = userSessions.get(email);
        return sessions != null ? sessions.size() : 0;
    }

    /**
     * Immediately notifies active zones when a user toggles Ghost Mode.
     * When Ghost Mode is activated, existing public markers are promptly removed.
     */
    public void handleGhostModeToggled(User user) {
        Set<Long> zonesToNotify = new HashSet<>();

        zoneMembershipRepository.findByUserAndActiveTrue(user).ifPresent(m -> {
            zonesToNotify.add(m.getZone().getId());
        });

        Set<String> sessions = userSessions.get(user.getEmail());
        if (sessions != null) {
            for (String sessionId : sessions) {
                Set<Long> activeZones = sessionZones.get(sessionId);
                if (activeZones != null) {
                    zonesToNotify.addAll(activeZones);
                }
            }
        }

        for (Long zoneId : zonesToNotify) {
            broadcastPresenceEvent(zoneId, user, user.isGhostMode() ? "GHOST_ENABLED" : "GHOST_DISABLED");
        }
    }

    private void broadcastPresenceEvent(Long zoneId, User user, String status) {
        boolean isGhost = user.isGhostMode();
        String displayName = isGhost ? "Ghost Operator" : user.getName();
        Long reportedId = isGhost ? null : user.getId();

        PresenceEventDto event = new PresenceEventDto(
            reportedId,
            displayName,
            zoneId,
            status,
            isGhost,
            Instant.now()
        );

        // 1. Publish to Redis for other cluster instances
        redisMessagePublisher.publishPresenceEvent(zoneId, event);

        // 2. Broadcast to local subscribers
        String destination = "/topic/zone." + zoneId + ".presence";
        messagingTemplate.convertAndSend(destination, event);
    }
}
