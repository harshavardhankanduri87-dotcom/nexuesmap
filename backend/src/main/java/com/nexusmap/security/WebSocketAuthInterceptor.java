package com.nexusmap.security;

import com.nexusmap.model.User;
import com.nexusmap.repository.UserRepository;
import com.nexusmap.service.ChatService;
import com.nexusmap.service.PresenceService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.security.Principal;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class WebSocketAuthInterceptor implements ChannelInterceptor {

    private static final Logger log = LoggerFactory.getLogger(WebSocketAuthInterceptor.class);

    private final JwtTokenProvider tokenProvider;
    private final CustomUserDetailsService userDetailsService;
    private final UserRepository userRepository;
    private final ChatService chatService;
    private final PresenceService presenceService;

    public WebSocketAuthInterceptor(
            JwtTokenProvider tokenProvider,
            CustomUserDetailsService userDetailsService,
            UserRepository userRepository,
            @org.springframework.context.annotation.Lazy ChatService chatService,
            @org.springframework.context.annotation.Lazy PresenceService presenceService
    ) {
        this.tokenProvider = tokenProvider;
        this.userDetailsService = userDetailsService;
        this.userRepository = userRepository;
        this.chatService = chatService;
        this.presenceService = presenceService;
    }

    // Matches /topic/zone.{zoneId} and /topic/zone.{zoneId}.presence
    private static final Pattern ZONE_TOPIC_PATTERN = Pattern.compile("^/topic/zone\\.(\\d+)(?:\\.presence)?$");

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) return message;

        StompCommand command = accessor.getCommand();

        if (StompCommand.CONNECT.equals(command)) {
            handleConnect(accessor);
        } else if (StompCommand.SUBSCRIBE.equals(command)) {
            handleSubscribe(accessor);
        } else if (StompCommand.SEND.equals(command)) {
            handleSend(accessor);
        } else if (StompCommand.DISCONNECT.equals(command)) {
            handleDisconnect(accessor);
        }

        return message;
    }

    private static final Pattern ZONE_SEND_PATTERN = Pattern.compile("^/app/zone\\.(\\d+)\\.send$");

    private void handleSend(StompHeaderAccessor accessor) {
        Principal principal = accessor.getUser();
        if (principal == null) {
            throw new AccessDeniedException("STOMP send rejected: Anonymous users are not permitted to send messages.");
        }

        String destination = accessor.getDestination();
        if (destination == null) return;

        Matcher matcher = ZONE_SEND_PATTERN.matcher(destination);
        if (matcher.matches()) {
            Long zoneId = Long.parseLong(matcher.group(1));
            String email = principal.getName();

            User user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new AccessDeniedException("User account not found"));

            if (!chatService.isUserAuthorizedForZone(user, zoneId)) {
                throw new AccessDeniedException(
                    "STOMP send denied: User '" + email + "' is not actively located inside zone " + zoneId
                );
            }
        }
    }

    private void handleConnect(StompHeaderAccessor accessor) {
        String token = extractToken(accessor);

        if (!StringUtils.hasText(token) || !tokenProvider.validateToken(token)) {
            throw new AuthenticationCredentialsNotFoundException("STOMP connection rejected: Missing or invalid JWT authentication token.");
        }

        String email = tokenProvider.getEmailFromToken(token);
        UserDetails userDetails = userDetailsService.loadUserByUsername(email);
        UsernamePasswordAuthenticationToken authentication =
            new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());

        accessor.setUser(authentication);
        presenceService.registerSession(accessor.getSessionId(), email);
        log.info("STOMP WebSocket authenticated for user '{}' (session: {})", email, accessor.getSessionId());
    }

    private void handleSubscribe(StompHeaderAccessor accessor) {
        Principal principal = accessor.getUser();
        if (principal == null) {
            throw new AccessDeniedException("STOMP subscription rejected: Anonymous access is not permitted.");
        }

        String destination = accessor.getDestination();
        if (destination == null) return;

        Matcher matcher = ZONE_TOPIC_PATTERN.matcher(destination);
        if (matcher.matches()) {
            Long zoneId = Long.parseLong(matcher.group(1));
            String email = principal.getName();

            User user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new AccessDeniedException("User account not found"));

            // Verify server-side zone membership authorization
            if (!chatService.isUserAuthorizedForZone(user, zoneId)) {
                throw new AccessDeniedException(
                    "STOMP subscription denied: User '" + email + "' is not actively located inside zone " + zoneId
                );
            }

            presenceService.joinZonePresence(accessor.getSessionId(), zoneId);
            log.info("Authorized subscription to '{}' for user '{}'", destination, email);
        }
    }

    private void handleDisconnect(StompHeaderAccessor accessor) {
        String sessionId = accessor.getSessionId();
        if (sessionId != null) {
            presenceService.removeSession(sessionId);
        }
    }

    private String extractToken(StompHeaderAccessor accessor) {
        // 1. Check native 'Authorization' header
        List<String> authHeaders = accessor.getNativeHeader("Authorization");
        if (authHeaders != null && !authHeaders.isEmpty()) {
            String header = authHeaders.get(0);
            if (StringUtils.hasText(header) && header.startsWith("Bearer ")) {
                return header.substring(7);
            }
        }

        // 2. Check native 'token' header
        List<String> tokenHeaders = accessor.getNativeHeader("token");
        if (tokenHeaders != null && !tokenHeaders.isEmpty()) {
            return tokenHeaders.get(0);
        }

        // 3. Check 'cookie' header
        List<String> cookieHeaders = accessor.getNativeHeader("cookie");
        if (cookieHeaders != null && !cookieHeaders.isEmpty()) {
            String cookieStr = cookieHeaders.get(0);
            for (String part : cookieStr.split(";")) {
                part = part.trim();
                if (part.startsWith(JwtAuthenticationFilter.COOKIE_NAME + "=")) {
                    return part.substring((JwtAuthenticationFilter.COOKIE_NAME + "=").length());
                }
            }
        }

        return null;
    }
}
