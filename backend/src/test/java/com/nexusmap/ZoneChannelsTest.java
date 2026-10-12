package com.nexusmap;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexusmap.dto.ChatMessageDto;
import com.nexusmap.dto.PaginatedMessagesResponse;
import com.nexusmap.dto.SendChatMessageRequest;
import com.nexusmap.model.*;
import com.nexusmap.repository.ChatMessageRepository;
import com.nexusmap.repository.UserRepository;
import com.nexusmap.repository.ZoneMembershipRepository;
import com.nexusmap.repository.ZoneRepository;
import com.nexusmap.security.JwtTokenProvider;
import com.nexusmap.security.WebSocketAuthInterceptor;
import com.nexusmap.service.ChatService;
import com.nexusmap.service.PresenceService;
import com.nexusmap.service.redis.RedisMessagePublisher;
import com.nexusmap.util.GeometryUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ZoneChannelsTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ChatService chatService;

    @Autowired
    private PresenceService presenceService;

    @Autowired
    private WebSocketAuthInterceptor webSocketAuthInterceptor;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ZoneRepository zoneRepository;

    @Autowired
    private ZoneMembershipRepository zoneMembershipRepository;

    @Autowired
    private ChatMessageRepository chatMessageRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private RedisMessagePublisher redisMessagePublisher;

    @MockBean
    private StringRedisTemplate stringRedisTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final MessageChannel messageChannel = mock(MessageChannel.class);

    private User insideUser;
    private User outsideUser;
    private User ghostUser;
    private Zone testZone;
    private String insideUserToken;
    private String outsideUserToken;

    @BeforeEach
    void setUp() {
        chatMessageRepository.deleteAll();
        zoneMembershipRepository.deleteAll();
        userRepository.deleteAll();

        // 1. Create Zone
        testZone = zoneRepository.findByCode("ZONE-LIBRARY").orElseGet(() -> {
            return zoneRepository.save(Zone.builder()
                .name("Library Test Zone")
                .code("ZONE-LIBRARY")
                .zoneType(ZoneType.ACADEMIC)
                .active(true)
                .centerLat(37.7745)
                .centerLng(-122.4200)
                .boundary(GeometryUtils.createPolygon(List.of(
                    List.of(-122.4220, 37.7730),
                    List.of(-122.4180, 37.7730),
                    List.of(-122.4180, 37.7760),
                    List.of(-122.4220, 37.7760)
                )))
                .build());
        });

        // 2. Create Users
        insideUser = userRepository.save(User.builder()
            .name("Inside Member")
            .email("inside@nexus.edu")
            .password(passwordEncoder.encode("Pass123!"))
            .role(Role.ROLE_USER)
            .ghostMode(false)
            .build());

        outsideUser = userRepository.save(User.builder()
            .name("Outside Visitor")
            .email("outside@nexus.edu")
            .password(passwordEncoder.encode("Pass123!"))
            .role(Role.ROLE_USER)
            .ghostMode(false)
            .build());

        ghostUser = userRepository.save(User.builder()
            .name("Stealth Operator")
            .email("ghost@nexus.edu")
            .password(passwordEncoder.encode("Pass123!"))
            .role(Role.ROLE_USER)
            .ghostMode(true)
            .build());

        // 3. Grant active membership in testZone to insideUser and ghostUser
        zoneMembershipRepository.save(ZoneMembership.builder()
            .user(insideUser)
            .zone(testZone)
            .active(true)
            .build());

        zoneMembershipRepository.save(ZoneMembership.builder()
            .user(ghostUser)
            .zone(testZone)
            .active(true)
            .build());

        insideUserToken = jwtTokenProvider.generateToken(insideUser);
        outsideUserToken = jwtTokenProvider.generateToken(outsideUser);
    }

    @Test
    @DisplayName("TASK 1 & 7: STOMP CONNECT should authenticate successfully with valid JWT")
    void shouldAuthenticateStompConnectWithValidJwt() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setNativeHeader("Authorization", "Bearer " + insideUserToken);
        accessor.setSessionId("session-101");
        accessor.setLeaveMutable(true);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        Message<?> processed = webSocketAuthInterceptor.preSend(message, messageChannel);
        StompHeaderAccessor processedAccessor = StompHeaderAccessor.wrap(processed);

        assertThat(processedAccessor.getUser()).isNotNull();
        assertThat(processedAccessor.getUser().getName()).isEqualTo("inside@nexus.edu");
        assertThat(presenceService.isUserOnline("inside@nexus.edu")).isTrue();
    }

    @Test
    @DisplayName("TASK 1 & 7: STOMP CONNECT should throw exception when JWT is missing or invalid")
    void shouldRejectStompConnectWithMissingOrInvalidJwt() {
        StompHeaderAccessor accessorNoToken = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessorNoToken.setSessionId("session-102");
        accessorNoToken.setLeaveMutable(true);
        Message<byte[]> msg1 = MessageBuilder.createMessage(new byte[0], accessorNoToken.getMessageHeaders());

        assertThatThrownBy(() -> webSocketAuthInterceptor.preSend(msg1, messageChannel))
            .isInstanceOf(AuthenticationCredentialsNotFoundException.class);

        StompHeaderAccessor accessorBadToken = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessorBadToken.setNativeHeader("Authorization", "Bearer invalid.jwt.token");
        accessorBadToken.setSessionId("session-103");
        accessorBadToken.setLeaveMutable(true);
        Message<byte[]> msg2 = MessageBuilder.createMessage(new byte[0], accessorBadToken.getMessageHeaders());

        assertThatThrownBy(() -> webSocketAuthInterceptor.preSend(msg2, messageChannel))
            .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
    }

    @Test
    @DisplayName("TASK 2 & 7: STOMP SUBSCRIBE should permit subscription for authorized zone member")
    void shouldAuthorizeSubscriptionForActiveZoneMember() {
        // First connect
        StompHeaderAccessor connectAccessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        connectAccessor.setNativeHeader("Authorization", "Bearer " + insideUserToken);
        connectAccessor.setSessionId("session-201");
        connectAccessor.setLeaveMutable(true);
        Message<?> connectMsg = MessageBuilder.createMessage(new byte[0], connectAccessor.getMessageHeaders());
        Message<?> connected = webSocketAuthInterceptor.preSend(connectMsg, messageChannel);
        StompHeaderAccessor connectedAccessor = StompHeaderAccessor.wrap(connected);

        // Then subscribe
        StompHeaderAccessor subAccessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        subAccessor.setSessionId("session-201");
        subAccessor.setDestination("/topic/zone." + testZone.getId());
        subAccessor.setUser(connectedAccessor.getUser());
        subAccessor.setLeaveMutable(true);
        Message<?> subMsg = MessageBuilder.createMessage(new byte[0], subAccessor.getMessageHeaders());

        Message<?> processed = webSocketAuthInterceptor.preSend(subMsg, messageChannel);
        assertThat(processed).isNotNull();
    }

    @Test
    @DisplayName("TASK 2 & 7: STOMP SUBSCRIBE should reject subscription when user lacks active zone membership")
    void shouldRejectSubscriptionForUnauthorizedUser() {
        // Connect as outside user
        StompHeaderAccessor connectAccessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        connectAccessor.setNativeHeader("Authorization", "Bearer " + outsideUserToken);
        connectAccessor.setSessionId("session-202");
        connectAccessor.setLeaveMutable(true);
        Message<?> connectMsg = MessageBuilder.createMessage(new byte[0], connectAccessor.getMessageHeaders());
        Message<?> connected = webSocketAuthInterceptor.preSend(connectMsg, messageChannel);
        StompHeaderAccessor connectedAccessor = StompHeaderAccessor.wrap(connected);

        // Subscribe to testZone where outsideUser is NOT a member
        StompHeaderAccessor subAccessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        subAccessor.setSessionId("session-202");
        subAccessor.setDestination("/topic/zone." + testZone.getId());
        subAccessor.setUser(connectedAccessor.getUser());
        subAccessor.setLeaveMutable(true);
        Message<?> subMsg = MessageBuilder.createMessage(new byte[0], subAccessor.getMessageHeaders());

        assertThatThrownBy(() -> webSocketAuthInterceptor.preSend(subMsg, messageChannel))
            .isInstanceOf(AccessDeniedException.class)
            .hasMessageContaining("not actively located inside zone");
    }

    @Test
    @DisplayName("TASK 3: Should successfully persist and deliver message for authorized user")
    void shouldPersistAndDeliverMessage() {
        SendChatMessageRequest request = new SendChatMessageRequest(testZone.getId(), "Hello Library Study Group!");
        ChatMessageDto result = chatService.sendMessage(insideUser, request);

        assertThat(result.id()).isNotNull();
        assertThat(result.content()).isEqualTo("Hello Library Study Group!");
        assertThat(result.senderName()).isEqualTo("Inside Member");
        assertThat(result.zoneId()).isEqualTo(testZone.getId());

        // Verify in PostgreSQL
        List<ChatMessage> persisted = chatMessageRepository.findAll();
        assertThat(persisted).hasSize(1);
        assertThat(persisted.get(0).getContent()).isEqualTo("Hello Library Study Group!");
    }

    @Test
    @DisplayName("TASK 3 & 7: Should reject message sending when user is not authorized in the zone")
    void shouldRejectMessageSendingForUnauthorizedUser() {
        SendChatMessageRequest request = new SendChatMessageRequest(testZone.getId(), "Intruder message!");

        assertThatThrownBy(() -> chatService.sendMessage(outsideUser, request))
            .isInstanceOf(AccessDeniedException.class)
            .hasMessageContaining("not currently inside this geofenced zone");

        assertThat(chatMessageRepository.count()).isEqualTo(0);
    }

    @Test
    @DisplayName("TASK 3 & 5: Ghost Mode sender should mask identity in messages")
    void shouldMaskIdentityInGhostMode() {
        SendChatMessageRequest request = new SendChatMessageRequest(testZone.getId(), "Stealth node online.");
        ChatMessageDto result = chatService.sendMessage(ghostUser, request);

        assertThat(result.ghostSender()).isTrue();
        assertThat(result.senderId()).isNull(); // Concealed ID
        assertThat(result.senderName()).contains("Ghost Operator");
    }

    @Test
    @DisplayName("TASK 3 & 7: Should enforce message rate limits (reject spam)")
    void shouldEnforceRateLimiting() {
        SendChatMessageRequest request = new SendChatMessageRequest(testZone.getId(), "Rapid ping");

        // Send 5 permitted messages
        for (int i = 0; i < 5; i++) {
            chatService.sendMessage(insideUser, request);
        }

        // 6th message in same window should trigger 429
        assertThatThrownBy(() -> chatService.sendMessage(insideUser, request))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("rate limit exceeded");
    }

    @Test
    @DisplayName("TASK 4: Should handle Redis publishing failure gracefully without dropping message")
    void shouldHandleRedisFailureGracefully() {
        // Mock Redis throwing an exception
        doThrow(new RedisConnectionFailureException("Redis connection refused"))
            .when(stringRedisTemplate).convertAndSend(anyString(), anyString());

        SendChatMessageRequest request = new SendChatMessageRequest(testZone.getId(), "Resilient message during Redis outage");
        ChatMessageDto result = chatService.sendMessage(insideUser, request);

        // Verification: Message is still safely persisted in PostgreSQL despite Redis failure
        assertThat(result.id()).isNotNull();
        assertThat(chatMessageRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("TASK 3 & 5: REST message history endpoint should return paginated history for authorized members")
    void shouldReturnPaginatedMessageHistory() throws Exception {
        // Populate 3 messages
        chatService.sendMessage(insideUser, new SendChatMessageRequest(testZone.getId(), "Message 1"));
        chatService.sendMessage(insideUser, new SendChatMessageRequest(testZone.getId(), "Message 2"));

        mockMvc.perform(get("/api/zones/" + testZone.getId() + "/messages")
                .header("Authorization", "Bearer " + insideUserToken)
                .param("page", "0")
                .param("size", "10"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.messages", hasSize(2)))
            .andExpect(jsonPath("$.totalElements").value(2));

        // Unauthorized user should be rejected with 403 Forbidden
        mockMvc.perform(get("/api/zones/" + testZone.getId() + "/messages")
                .header("Authorization", "Bearer " + outsideUserToken))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("TASK 5: Should track connection lifecycle across tabs and disconnects")
    void shouldTrackConnectionPresenceAndDisconnects() {
        presenceService.registerSession("tab-1", "inside@nexus.edu");
        presenceService.registerSession("tab-2", "inside@nexus.edu");
        assertThat(presenceService.getActiveSessionCount("inside@nexus.edu")).isEqualTo(2);

        // Close 1 tab
        presenceService.removeSession("tab-1");
        assertThat(presenceService.getActiveSessionCount("inside@nexus.edu")).isEqualTo(1);
        assertThat(presenceService.isUserOnline("inside@nexus.edu")).isTrue();

        // Close 2nd tab
        presenceService.removeSession("tab-2");
        assertThat(presenceService.getActiveSessionCount("inside@nexus.edu")).isEqualTo(0);
        assertThat(presenceService.isUserOnline("inside@nexus.edu")).isFalse();
    }
}
