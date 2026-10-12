package com.nexusmap.controller;

import com.nexusmap.dto.ChatMessageDto;
import com.nexusmap.dto.PaginatedMessagesResponse;
import com.nexusmap.dto.SendChatMessageRequest;
import com.nexusmap.model.User;
import com.nexusmap.repository.UserRepository;
import com.nexusmap.service.ChatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;
    private final UserRepository userRepository;

    /**
     * REST GET /api/zones/{zoneId}/messages - Paginated chat history for authorized zone members.
     */
    @GetMapping("/api/zones/{zoneId}/messages")
    public ResponseEntity<PaginatedMessagesResponse> getZoneMessages(
            @PathVariable Long zoneId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        if (userDetails == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        User user = userRepository.findByEmailIgnoreCase(userDetails.getUsername())
            .orElseThrow(() -> new UsernameNotFoundException("User not found: " + userDetails.getUsername()));

        PaginatedMessagesResponse history = chatService.getMessageHistory(user, zoneId, page, size);
        return ResponseEntity.ok(history);
    }

    /**
     * REST POST /api/zones/{zoneId}/messages - REST endpoint to post a message into an authorized zone.
     */
    @PostMapping("/api/zones/{zoneId}/messages")
    public ResponseEntity<ChatMessageDto> postZoneMessage(
            @PathVariable Long zoneId,
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        if (userDetails == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        User user = userRepository.findByEmailIgnoreCase(userDetails.getUsername())
            .orElseThrow(() -> new UsernameNotFoundException("User not found: " + userDetails.getUsername()));

        String content = body.get("content");
        SendChatMessageRequest request = new SendChatMessageRequest(zoneId, content);
        ChatMessageDto messageDto = chatService.sendMessage(user, request);

        return ResponseEntity.status(HttpStatus.CREATED).body(messageDto);
    }

    /**
     * STOMP @MessageMapping("/zone.{zoneId}.send") - Real-time WebSocket messaging destination.
     * Sender identity is strictly derived from the authenticated STOMP Principal.
     */
    @MessageMapping("/zone.{zoneId}.send")
    public void handleStompMessage(
            @DestinationVariable Long zoneId,
            @Payload Map<String, String> payload,
            Principal principal
    ) {
        if (principal == null) {
            throw new AccessDeniedException("Unauthenticated STOMP send request rejected");
        }

        User user = userRepository.findByEmailIgnoreCase(principal.getName())
            .orElseThrow(() -> new AccessDeniedException("User not found: " + principal.getName()));

        String content = payload != null ? payload.get("content") : null;
        SendChatMessageRequest request = new SendChatMessageRequest(zoneId, content);
        chatService.sendMessage(user, request);
    }
}
