package com.nexusmap.service;

import com.nexusmap.dto.ChatMessageDto;
import com.nexusmap.dto.PaginatedMessagesResponse;
import com.nexusmap.dto.SendChatMessageRequest;
import com.nexusmap.exception.ResourceNotFoundException;
import com.nexusmap.model.ChatMessage;
import com.nexusmap.model.Role;
import com.nexusmap.model.User;
import com.nexusmap.model.Zone;
import com.nexusmap.repository.ChatMessageRepository;
import com.nexusmap.repository.ZoneMembershipRepository;
import com.nexusmap.repository.ZoneRepository;
import com.nexusmap.service.redis.RedisMessagePublisher;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    private final ChatMessageRepository chatMessageRepository;
    private final ZoneRepository zoneRepository;
    private final ZoneMembershipRepository zoneMembershipRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final RedisMessagePublisher redisMessagePublisher;

    public ChatService(
            ChatMessageRepository chatMessageRepository,
            ZoneRepository zoneRepository,
            ZoneMembershipRepository zoneMembershipRepository,
            @org.springframework.context.annotation.Lazy SimpMessagingTemplate messagingTemplate,
            RedisMessagePublisher redisMessagePublisher
    ) {
        this.chatMessageRepository = chatMessageRepository;
        this.zoneRepository = zoneRepository;
        this.zoneMembershipRepository = zoneMembershipRepository;
        this.messagingTemplate = messagingTemplate;
        this.redisMessagePublisher = redisMessagePublisher;
    }

    // In-memory rate limiting: userId -> list of recent message timestamps (max 5 per 3 seconds)
    private final Map<Long, List<Long>> rateLimitMap = new ConcurrentHashMap<>();
    private static final int MAX_MESSAGES_PER_WINDOW = 5;
    private static final long RATE_LIMIT_WINDOW_MILLIS = 3000;

    /**
     * Checks whether a user is authorized to send/receive messages in a zone.
     * User must have an active ZoneMembership in that zone or possess ROLE_ADMIN.
     */
    @Transactional(readOnly = true)
    public boolean isUserAuthorizedForZone(User user, Long zoneId) {
        if (user.getRole() == Role.ROLE_ADMIN) {
            return true;
        }

        return zoneMembershipRepository.findByUserAndZoneIdAndActiveTrue(user, zoneId).isPresent();
    }

    /**
     * Sends, persists, and broadcasts a message to a zone channel.
     * Sender identity is strictly derived from the authenticated User.
     */
    @Transactional
    public ChatMessageDto sendMessage(User user, SendChatMessageRequest request) {
        Long zoneId = request.zoneId();

        // 1. Authorization check
        if (!isUserAuthorizedForZone(user, zoneId)) {
            throw new AccessDeniedException("You are not currently inside this geofenced zone and cannot broadcast messages.");
        }

        // 2. Rate limiting check
        enforceRateLimit(user.getId());

        // 3. Message validation & sanitization
        String rawContent = request.content() != null ? request.content().trim() : "";
        if (rawContent.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Message content cannot be blank.");
        }
        if (rawContent.length() > 1000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Message exceeds 1000 character limit.");
        }
        String content = sanitizeHtml(rawContent);

        Zone zone = zoneRepository.findById(zoneId)
            .orElseThrow(() -> new ResourceNotFoundException("Zone with ID " + zoneId + " not found"));

        // 4. Persistence in PostgreSQL (Durable source of truth)
        ChatMessage message = ChatMessage.builder()
            .sender(user)
            .zone(zone)
            .content(content)
            .build();
        ChatMessage saved = chatMessageRepository.save(message);

        ChatMessageDto dto = ChatMessageDto.fromEntity(saved);

        // 5. Distributed fanout via Redis Pub/Sub
        redisMessagePublisher.publishChatMessage(zoneId, dto);

        // 6. Local STOMP broadcast
        String destination = "/topic/zone." + zoneId;
        messagingTemplate.convertAndSend(destination, dto);

        return dto;
    }

    /**
     * Retrieves paginated chat message history for an authorized user.
     */
    @Transactional(readOnly = true)
    public PaginatedMessagesResponse getMessageHistory(User user, Long zoneId, int page, int size) {
        if (!isUserAuthorizedForZone(user, zoneId)) {
            throw new AccessDeniedException("You must be actively located within this zone to access its message history.");
        }

        Zone zone = zoneRepository.findById(zoneId)
            .orElseThrow(() -> new ResourceNotFoundException("Zone with ID " + zoneId + " not found"));

        Pageable pageable = PageRequest.of(page, Math.min(size, 100));
        Page<ChatMessage> messagePage = chatMessageRepository.findByZoneOrderByCreatedAtDesc(zone, pageable);

        List<ChatMessageDto> dtoList = messagePage.getContent().stream()
            .map(ChatMessageDto::fromEntity)
            .toList();

        return new PaginatedMessagesResponse(
            dtoList,
            messagePage.getNumber(),
            messagePage.getSize(),
            messagePage.getTotalPages(),
            messagePage.getTotalElements()
        );
    }

    private void enforceRateLimit(Long userId) {
        long now = System.currentTimeMillis();
        rateLimitMap.compute(userId, (k, timestamps) -> {
            if (timestamps == null) {
                return new java.util.ArrayList<>(List.of(now));
            }
            // Remove timestamps outside window
            timestamps.removeIf(t -> (now - t) > RATE_LIMIT_WINDOW_MILLIS);
            if (timestamps.size() >= MAX_MESSAGES_PER_WINDOW) {
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Message rate limit exceeded. Please wait a moment.");
            }
            timestamps.add(now);
            return timestamps;
        });
    }

    private String sanitizeHtml(String input) {
        if (input == null) return null;
        return input.replace("&", "&amp;")
                    .replace("<", "&lt;")
                    .replace(">", "&gt;");
    }
}
