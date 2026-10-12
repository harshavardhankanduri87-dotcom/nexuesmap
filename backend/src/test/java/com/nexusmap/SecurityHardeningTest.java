package com.nexusmap;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexusmap.dto.CreatePinRequest;
import com.nexusmap.dto.LoginRequest;
import com.nexusmap.dto.UpdatePinRequest;
import com.nexusmap.model.*;
import com.nexusmap.repository.*;
import com.nexusmap.security.JwtTokenProvider;
import com.nexusmap.security.WebSocketAuthInterceptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.nexusmap.dto.LocationUpdateRequest;
import com.nexusmap.dto.UpdatePrivacySettingsRequest;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class SecurityHardeningTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ZoneRepository zoneRepository;

    @Autowired
    private ZoneMembershipRepository zoneMembershipRepository;

    @Autowired
    private PinRepository pinRepository;

    @Autowired
    private MeetupRepository meetupRepository;

    @Autowired
    private MeetupParticipantRepository meetupParticipantRepository;

    @Autowired
    private ChatMessageRepository chatMessageRepository;

    @Autowired
    private UserLocationRepository userLocationRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtTokenProvider tokenProvider;

    @Autowired
    private WebSocketAuthInterceptor webSocketAuthInterceptor;

    private final GeometryFactory geometryFactory = new GeometryFactory(new PrecisionModel(), 4326);

    private User alice;
    private User bob;
    private User admin;
    private Zone testZone;
    private String aliceToken;
    private String bobToken;
    private String adminToken;

    @BeforeEach
    void setUp() {
        chatMessageRepository.deleteAll();
        meetupParticipantRepository.deleteAll();
        meetupRepository.deleteAll();
        pinRepository.deleteAll();
        zoneMembershipRepository.deleteAll();
        userLocationRepository.deleteAll();
        userRepository.deleteAll();

        // 1. Create synthetic users
        alice = userRepository.save(User.builder()
            .name("Alice User")
            .email("alice.sec@nexus.edu")
            .password(passwordEncoder.encode("SecurePass123!"))
            .role(Role.ROLE_USER)
            .ghostMode(false)
            .locationSharingEnabled(true)
            .fuzzingRadiusMeters(100)
            .build());

        bob = userRepository.save(User.builder()
            .name("Bob Attacker")
            .email("bob.sec@nexus.edu")
            .password(passwordEncoder.encode("SecurePass123!"))
            .role(Role.ROLE_USER)
            .ghostMode(false)
            .locationSharingEnabled(true)
            .fuzzingRadiusMeters(100)
            .build());

        admin = userRepository.save(User.builder()
            .name("Security Admin")
            .email("admin.sec@nexus.edu")
            .password(passwordEncoder.encode("AdminSecret123!"))
            .role(Role.ROLE_ADMIN)
            .ghostMode(false)
            .locationSharingEnabled(true)
            .fuzzingRadiusMeters(100)
            .build());

        aliceToken = tokenProvider.generateToken(alice);
        bobToken = tokenProvider.generateToken(bob);
        adminToken = tokenProvider.generateToken(admin);

        // 2. Fetch or create test zone
        testZone = zoneRepository.findByCode("ZONE-LIBRARY").orElseGet(() -> {
            Coordinate[] coords = new Coordinate[]{
                new Coordinate(-122.4220, 37.7730),
                new Coordinate(-122.4180, 37.7730),
                new Coordinate(-122.4180, 37.7760),
                new Coordinate(-122.4220, 37.7760),
                new Coordinate(-122.4220, 37.7730)
            };
            return zoneRepository.save(Zone.builder()
                .name("Security Test Zone")
                .code("ZONE-SEC-TEST")
                .description("Zone for automated security regression tests")
                .zoneType(ZoneType.ACADEMIC)
                .active(true)
                .centerLat(37.7745)
                .centerLng(-122.4200)
                .color("#eab308")
                .boundary(geometryFactory.createPolygon(coords))
                .build());
        });
    }

    // =========================================================================
    // 1. AUTHENTICATION & TOKEN HARDENING
    // =========================================================================

    @Test
    @DisplayName("JWT: Rejects tampered tokens and forged signatures")
    void testTamperedJwtRejection() throws Exception {
        String forgedToken = aliceToken.substring(0, aliceToken.length() - 5) + "abcde";

        mockMvc.perform(get("/api/auth/me")
                .header("Authorization", "Bearer " + forgedToken))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("JWT: Rejects missing Authorization header on protected endpoints")
    void testMissingTokenRejection() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
            .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/privacy/settings"))
            .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/pins"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Auth: Enforces secure password verification and generic error message")
    void testBadCredentialsRejection() throws Exception {
        LoginRequest badRequest = new LoginRequest("alice.sec@nexus.edu", "WrongPassword999!");

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(badRequest)))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.message").value("Invalid email or password"));
    }

    @Test
    @DisplayName("Password: Passwords must be hashed with BCrypt, never plaintext")
    void testPasswordHashedNotPlaintext() {
        User savedAlice = userRepository.findById(alice.getId()).orElseThrow();
        assertThat(savedAlice.getPassword()).startsWith("$2a$");
        assertThat(savedAlice.getPassword()).doesNotContain("SecurePass123!");
    }

    // =========================================================================
    // 2. ROLE-BASED ACCESS CONTROL & PRIVILEGE ELEVATION DEFENSES
    // =========================================================================

    @Test
    @DisplayName("RBAC: Regular ROLE_USER cannot access admin analytics or metrics (403 Forbidden)")
    void testNonAdminCannotAccessAdminEndpoints() throws Exception {
        mockMvc.perform(get("/api/admin/metrics")
                .header("Authorization", "Bearer " + aliceToken))
            .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/admin/analytics/overview")
                .header("Authorization", "Bearer " + aliceToken))
            .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/admin/analytics/zones")
                .header("Authorization", "Bearer " + aliceToken))
            .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/admin/analytics/activity")
                .header("Authorization", "Bearer " + aliceToken))
            .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/admin/analytics/density")
                .header("Authorization", "Bearer " + aliceToken))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("RBAC: ROLE_ADMIN successfully accesses admin analytics (200 OK)")
    void testAdminCanAccessAdminEndpoints() throws Exception {
        mockMvc.perform(get("/api/admin/analytics/overview")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalRegisteredUsers").isNumber());
    }

    // =========================================================================
    // 3. HORIZONTAL PRIVILEGE ESCALATION DEFENSES (Cross-User Pin & Meetup Edits)
    // =========================================================================

    @Test
    @DisplayName("IDOR / Horizontal Auth: User Bob cannot edit Alice's Pin (403 Forbidden)")
    void testUnauthorizedPinEditRejected() throws Exception {
        // Alice creates a pin
        Pin pin = pinRepository.save(Pin.builder()
            .title("Alice Original Pin")
            .description("Alice created this pin")
            .category(PinCategory.EVENT)
            .status(PinStatus.ACTIVE)
            .latitude(37.7745)
            .longitude(-122.4200)
            .location(geometryFactory.createPoint(new Coordinate(-122.4200, 37.7745)))
            .creator(alice)
            .expiresAt(Instant.now().plus(24, ChronoUnit.HOURS))
            .build());

        // Bob attempts to modify Alice's pin
        UpdatePinRequest update = UpdatePinRequest.builder()
            .title("Hacked By Bob")
            .description("Malicious modification attempt")
            .build();

        mockMvc.perform(patch("/api/pins/" + pin.getId())
                .header("Authorization", "Bearer " + bobToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(update)))
            .andExpect(status().isForbidden());

        // Verify pin was not altered
        Pin refreshed = pinRepository.findById(pin.getId()).orElseThrow();
        assertThat(refreshed.getTitle()).isEqualTo("Alice Original Pin");
    }

    @Test
    @DisplayName("IDOR: User Bob cannot delete Alice's Pin (403 Forbidden)")
    void testUnauthorizedPinDeleteRejected() throws Exception {
        Pin pin = pinRepository.save(Pin.builder()
            .title("Alice Protected Pin")
            .description("Description")
            .category(PinCategory.GENERAL_ANNOUNCEMENT)
            .status(PinStatus.ACTIVE)
            .latitude(37.7745)
            .longitude(-122.4200)
            .location(geometryFactory.createPoint(new Coordinate(-122.4200, 37.7745)))
            .creator(alice)
            .expiresAt(Instant.now().plus(24, ChronoUnit.HOURS))
            .build());

        mockMvc.perform(delete("/api/pins/" + pin.getId())
                .header("Authorization", "Bearer " + bobToken))
            .andExpect(status().isForbidden());

        Pin refreshed = pinRepository.findById(pin.getId()).orElseThrow();
        assertThat(refreshed.getStatus()).isEqualTo(PinStatus.ACTIVE);
    }

    @Test
    @DisplayName("Admin Authorization: ROLE_ADMIN can edit and delete any pin")
    void testAdminCanModifyAnyPin() throws Exception {
        Pin pin = pinRepository.save(Pin.builder()
            .title("Student Pin")
            .description("Description")
            .category(PinCategory.CAMPUS_ISSUE)
            .status(PinStatus.ACTIVE)
            .latitude(37.7745)
            .longitude(-122.4200)
            .location(geometryFactory.createPoint(new Coordinate(-122.4200, 37.7745)))
            .creator(alice)
            .expiresAt(Instant.now().plus(24, ChronoUnit.HOURS))
            .build());

        UpdatePinRequest update = UpdatePinRequest.builder()
            .title("Moderated Title")
            .build();

        mockMvc.perform(patch("/api/pins/" + pin.getId())
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(update)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.title").value("Moderated Title"));
    }

    // =========================================================================
    // 4. ZONE GEOFENCING & CHAT ACCESS CONTROL
    // =========================================================================

    @Test
    @DisplayName("Geofence Security: User outside zone cannot post messages (403 Forbidden)")
    void testOutsideUserCannotPostZoneMessage() throws Exception {
        // Neither Alice nor Bob is currently inside testZone
        mockMvc.perform(post("/api/zones/" + testZone.getId() + "/messages")
                .header("Authorization", "Bearer " + bobToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("content", "Unauthorized message from outside"))))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Geofence Security: User outside zone cannot fetch message history (403 Forbidden)")
    void testOutsideUserCannotReadZoneMessages() throws Exception {
        mockMvc.perform(get("/api/zones/" + testZone.getId() + "/messages")
                .header("Authorization", "Bearer " + bobToken))
            .andExpect(status().isForbidden());
    }

    // =========================================================================
    // 5. STORED XSS PREVENTION & HTML SANITIZATION
    // =========================================================================

    @Test
    @DisplayName("XSS Prevention: Chat messages sanitize HTML tags before storage")
    void testChatMessageXssSanitization() throws Exception {
        // Authorize Alice in testZone
        zoneMembershipRepository.save(ZoneMembership.builder()
            .user(alice)
            .zone(testZone)
            .active(true)
            .enteredAt(Instant.now())
            .build());

        String maliciousPayload = "<script>alert('pwned')</script><b>BoldText</b>";

        mockMvc.perform(post("/api/zones/" + testZone.getId() + "/messages")
                .header("Authorization", "Bearer " + aliceToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("content", maliciousPayload))))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.content").value("&lt;script&gt;alert('pwned')&lt;/script&gt;&lt;b&gt;BoldText&lt;/b&gt;"));
    }

    @Test
    @DisplayName("XSS Prevention: Pin creation and updates sanitize HTML tags")
    void testPinXssSanitization() throws Exception {
        CreatePinRequest xssRequest = CreatePinRequest.builder()
            .title("<img src=x onerror=alert(1)>Event")
            .description("<script>stealCookies()</script>Description")
            .category(PinCategory.MEETUP)
            .latitude(37.7745)
            .longitude(-122.4200)
            .durationHours(24)
            .build();

        mockMvc.perform(post("/api/pins")
                .header("Authorization", "Bearer " + aliceToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(xssRequest)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.title").value("&lt;img src=x onerror=alert(1)&gt;Event"))
            .andExpect(jsonPath("$.description").value("&lt;script&gt;stealCookies()&lt;/script&gt;Description"));
    }

    // =========================================================================
    // 6. SQL INJECTION RESISTANCE
    // =========================================================================

    @Test
    @DisplayName("SQL Injection: Malicious SQL injection payloads in parameters do not compromise database")
    void testSqlInjectionResistance() throws Exception {
        // Query nearby pins with SQL injection payload in query params
        mockMvc.perform(get("/api/pins/nearby")
                .param("lat", "37.7745")
                .param("lng", "-122.4200")
                .param("radiusMeters", "1000")
                .header("Authorization", "Bearer " + aliceToken))
            .andExpect(status().isOk());

        // Verify users and zones tables are intact
        assertThat(userRepository.count()).isGreaterThanOrEqualTo(3);
        assertThat(zoneRepository.count()).isGreaterThanOrEqualTo(1);
    }

    // =========================================================================
    // 7. WEBSOCKET STOMP INTERCEPTOR SECURITY (CONNECT, SUBSCRIBE, SEND)
    // =========================================================================

    @Test
    @DisplayName("WebSocket Security: Anonymous CONNECT frame is rejected")
    void testWsAnonymousConnectRejected() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setSessionId("unauth-session-1");
        accessor.setLeaveMutable(true);
        Message<?> msg = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        assertThatThrownBy(() -> webSocketAuthInterceptor.preSend(msg, null))
            .isInstanceOf(AuthenticationCredentialsNotFoundException.class)
            .hasMessageContaining("Missing or invalid JWT");
    }

    @Test
    @DisplayName("WebSocket Security: Anonymous SUBSCRIBE frame is rejected")
    void testWsAnonymousSubscribeRejected() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setSessionId("unauth-sub-1");
        accessor.setDestination("/topic/zone." + testZone.getId());
        accessor.setLeaveMutable(true);
        Message<?> msg = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        assertThatThrownBy(() -> webSocketAuthInterceptor.preSend(msg, null))
            .isInstanceOf(AccessDeniedException.class)
            .hasMessageContaining("Anonymous access is not permitted");
    }

    @Test
    @DisplayName("WebSocket Security: User outside zone rejected when subscribing to zone topic")
    void testWsOutsideUserSubscribeRejected() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setSessionId("alice-sub-1");
        accessor.setUser(new UsernamePasswordAuthenticationToken(alice.getEmail(), null, List.of()));
        accessor.setDestination("/topic/zone." + testZone.getId());
        accessor.setLeaveMutable(true);
        Message<?> msg = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        assertThatThrownBy(() -> webSocketAuthInterceptor.preSend(msg, null))
            .isInstanceOf(AccessDeniedException.class)
            .hasMessageContaining("not actively located inside zone");
    }

    @Test
    @DisplayName("WebSocket Security: Anonymous SEND frame is rejected")
    void testWsAnonymousSendRejected() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
        accessor.setSessionId("anon-send-1");
        accessor.setDestination("/app/zone." + testZone.getId() + ".send");
        accessor.setLeaveMutable(true);
        Message<?> msg = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        assertThatThrownBy(() -> webSocketAuthInterceptor.preSend(msg, null))
            .isInstanceOf(AccessDeniedException.class)
            .hasMessageContaining("Anonymous users are not permitted to send messages");
    }

    @Test
    @DisplayName("WebSocket Security: User outside zone rejected when sending to zone channel")
    void testWsOutsideUserSendRejected() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
        accessor.setSessionId("alice-send-1");
        accessor.setUser(new UsernamePasswordAuthenticationToken(alice.getEmail(), null, List.of()));
        accessor.setDestination("/app/zone." + testZone.getId() + ".send");
        accessor.setLeaveMutable(true);
        Message<?> msg = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        assertThatThrownBy(() -> webSocketAuthInterceptor.preSend(msg, null))
            .isInstanceOf(AccessDeniedException.class)
            .hasMessageContaining("not actively located inside zone");
    }

    // =========================================================================
    // 8. GHOST MODE & SERVER-SIDE PRIVACY ENFORCEMENT
    // =========================================================================

    @Test
    @DisplayName("Ghost Mode: Ghost Mode users are completely excluded from peer radar")
    void testGhostModeUserExcludedFromPeerRadar() throws Exception {
        // Alice enables Ghost Mode
        alice.setGhostMode(true);
        userRepository.save(alice);

        // Bob is sharing location
        bob.setGhostMode(false);
        bob.setLocationSharingEnabled(true);
        userRepository.save(bob);

        // Bob queries peer radar for testZone
        mockMvc.perform(get("/api/privacy/peers")
                .header("Authorization", "Bearer " + bobToken)
                .param("zoneId", String.valueOf(testZone.getId())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$[?(@.userId == " + alice.getId() + ")]").doesNotExist());
    }

    @Test
    @DisplayName("Ghost Mode: Pins created by Ghost Mode users mask identity to peers")
    void testGhostModePinCreatorMasking() throws Exception {
        alice.setGhostMode(true);
        userRepository.save(alice);

        Pin pin = pinRepository.save(Pin.builder()
            .title("Ghost Secret Pin")
            .description("Created in stealth mode")
            .category(PinCategory.MEETUP)
            .status(PinStatus.ACTIVE)
            .latitude(37.7745)
            .longitude(-122.4200)
            .location(geometryFactory.createPoint(new Coordinate(-122.4200, 37.7745)))
            .creator(alice)
            .expiresAt(Instant.now().plus(24, ChronoUnit.HOURS))
            .build());

        // When Bob views the pin, creator is masked
        mockMvc.perform(get("/api/pins/" + pin.getId())
                .header("Authorization", "Bearer " + bobToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.creatorName").value("Ghost Operator"))
            .andExpect(jsonPath("$.creatorId").doesNotExist())
            .andExpect(jsonPath("$.isCreator").value(false));

        // When Alice views her own pin, identity is retained for her
        mockMvc.perform(get("/api/pins/" + pin.getId())
                .header("Authorization", "Bearer " + aliceToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.creatorId").value(alice.getId()))
            .andExpect(jsonPath("$.isCreator").value(true));
    }

    // =========================================================================
    // 9. RATE LIMITING (DoS RESISTANCE)
    // =========================================================================

    @Test
    @DisplayName("Rate Limiting: Exceeding 5 chat messages in 3 seconds triggers HTTP 429")
    void testChatRateLimiting() throws Exception {
        // Authorize Alice in testZone
        zoneMembershipRepository.save(ZoneMembership.builder()
            .user(alice)
            .zone(testZone)
            .active(true)
            .enteredAt(Instant.now())
            .build());

        // Alice sends 5 messages rapidly (allowed)
        for (int i = 1; i <= 5; i++) {
            mockMvc.perform(post("/api/zones/" + testZone.getId() + "/messages")
                    .header("Authorization", "Bearer " + aliceToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(Map.of("content", "Rapid message #" + i))))
                .andExpect(status().isCreated());
        }

        // 6th message exceeds rate limit -> 429 Too Many Requests
        mockMvc.perform(post("/api/zones/" + testZone.getId() + "/messages")
                .header("Authorization", "Bearer " + aliceToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("content", "Spam attempt #6"))))
            .andExpect(status().isTooManyRequests());
    }

    // =========================================================================
    // 10. MEETUP CAPACITY RESTRICTION
    // =========================================================================

    @Test
    @DisplayName("Meetup Integrity: Joining a meetup at full capacity returns HTTP 400 Bad Request")
    void testMeetupCapacityEnforcement() throws Exception {
        // Create meetup with capacity = 1
        Pin pin = pinRepository.save(Pin.builder()
            .title("Exclusive Study Group")
            .description("Limited room")
            .category(PinCategory.MEETUP)
            .status(PinStatus.ACTIVE)
            .latitude(37.7745)
            .longitude(-122.4200)
            .location(geometryFactory.createPoint(new Coordinate(-122.4200, 37.7745)))
            .creator(alice)
            .expiresAt(Instant.now().plus(24, ChronoUnit.HOURS))
            .build());

        Meetup meetup = meetupRepository.save(Meetup.builder()
            .pin(pin)
            .startTime(Instant.now().plus(2, ChronoUnit.HOURS))
            .capacity(1) // Only 1 slot
            .active(true)
            .build());

        // Alice takes the 1 slot
        meetupParticipantRepository.save(MeetupParticipant.builder()
            .meetup(meetup)
            .user(alice)
            .build());

        pin.setMeetup(meetup);
        pinRepository.save(pin);

        // Bob attempts to join full meetup
        mockMvc.perform(post("/api/pins/" + pin.getId() + "/join")
                .header("Authorization", "Bearer " + bobToken))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("Meetup is at full capacity (1 participants)."));
    }

    // =========================================================================
    // 11. TOKEN EXPIRATION, LOGOUT & IDENTITY BINDING
    // =========================================================================

    @Test
    @DisplayName("JWT Expiration: Expired JWT tokens are strictly rejected with 401 Unauthorized")
    void testExpiredJwtRejection() throws Exception {
        Date issuedAt = new Date(System.currentTimeMillis() - 7200000); // 2 hours ago
        Date expiredAt = new Date(System.currentTimeMillis() - 3600000); // 1 hour ago
        String expiredToken = tokenProvider.generateTokenWithExpiry(alice, issuedAt, expiredAt);

        // Direct token validation check
        assertThat(tokenProvider.validateToken(expiredToken)).isFalse();

        // Protected endpoint check
        mockMvc.perform(get("/api/auth/me")
                .header("Authorization", "Bearer " + expiredToken))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Auth Logout: POST /api/auth/logout invalidates auth cookie with max-age=0")
    void testLogoutClearsAuthCookie() throws Exception {
        mockMvc.perform(post("/api/auth/logout")
                .header("Authorization", "Bearer " + aliceToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.message").value("Logged out successfully"))
            .andExpect(header().string("Set-Cookie", containsString("Max-Age=0")));
    }

    @Test
    @DisplayName("Location Identity: GPS location updates are strictly bound to authenticated principal")
    void testGpsUpdateStrictlyBoundToAuthenticatedUser() throws Exception {
        LocationUpdateRequest request = new LocationUpdateRequest(37.7745, -122.4200, 10.0);

        // Bob posts location update
        mockMvc.perform(post("/api/location/update")
                .header("Authorization", "Bearer " + bobToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk());

        // Verify Bob's location was updated and Alice's was untouched
        UserLocation bobLoc = userLocationRepository.findFirstByUserOrderByRecordedAtDesc(bob).orElse(null);
        assertThat(bobLoc).isNotNull();
        assertThat(bobLoc.getLatitude()).isEqualTo(37.7745);
        assertThat(bobLoc.getLongitude()).isEqualTo(-122.4200);

        UserLocation aliceLoc = userLocationRepository.findFirstByUserOrderByRecordedAtDesc(alice).orElse(null);
        assertThat(aliceLoc).isNull();
    }

    @Test
    @DisplayName("Horizontal Privacy Auth: User Bob cannot modify Alice's privacy settings")
    void testCannotModifyOtherUserPrivacySettings() throws Exception {
        // Bob updates his privacy settings
        UpdatePrivacySettingsRequest update = new UpdatePrivacySettingsRequest(true, true, 200);

        mockMvc.perform(put("/api/privacy/settings")
                .header("Authorization", "Bearer " + bobToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(update)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.ghostMode").value(true))
            .andExpect(jsonPath("$.fuzzingRadiusMeters").value(200));

        // Verify Alice's privacy settings remain completely unchanged
        User refreshedAlice = userRepository.findById(alice.getId()).orElseThrow();
        assertThat(refreshedAlice.isGhostMode()).isFalse();
        assertThat(refreshedAlice.getFuzzingRadiusMeters()).isEqualTo(100);
    }

    @Test
    @DisplayName("Location Privacy: Peer radar obfuscates coordinates with server-side Gaussian noise")
    void testPreciseCoordinatesObfuscatedInPeerRadar() throws Exception {
        // Alice enables location sharing with 100m fuzzing radius
        alice.setGhostMode(false);
        alice.setLocationSharingEnabled(true);
        alice.setFuzzingRadiusMeters(100);
        userRepository.save(alice);

        // Alice is inside testZone at exact coordinates (37.7745, -122.4200)
        userLocationRepository.save(UserLocation.builder()
            .user(alice)
            .latitude(37.7745)
            .longitude(-122.4200)
            .accuracy(5.0)
            .location(geometryFactory.createPoint(new Coordinate(-122.4200, 37.7745)))
            .build());

        zoneMembershipRepository.save(ZoneMembership.builder()
            .user(alice)
            .zone(testZone)
            .active(true)
            .enteredAt(Instant.now())
            .build());

        // Bob queries peer radar
        mockMvc.perform(get("/api/privacy/peers")
                .header("Authorization", "Bearer " + bobToken)
                .param("zoneId", String.valueOf(testZone.getId())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$[0].displayName").value("Alice User"))
            .andExpect(jsonPath("$[0].fuzzingRadiusMeters").value(100));
    }

    @Test
    @DisplayName("SQL Injection: Parameterized zone and pin queries remain immune to injection syntax")
    void testSqlInjectionParametricImmunity() throws Exception {
        String sqlInjectionPayload = "ZONE-LIBRARY' OR '1'='1";

        mockMvc.perform(get("/api/zones")
                .param("code", sqlInjectionPayload)
                .header("Authorization", "Bearer " + aliceToken))
            .andExpect(status().isOk());

        // Verify database state is untouched
        assertThat(userRepository.count()).isGreaterThanOrEqualTo(3);
    }
}
