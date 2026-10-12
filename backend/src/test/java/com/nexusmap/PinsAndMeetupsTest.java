package com.nexusmap;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexusmap.dto.CreateMeetupDetailsDto;
import com.nexusmap.dto.CreatePinRequest;
import com.nexusmap.dto.UpdatePinRequest;
import com.nexusmap.model.*;
import com.nexusmap.repository.MeetupParticipantRepository;
import com.nexusmap.repository.MeetupRepository;
import com.nexusmap.repository.PinRepository;
import com.nexusmap.repository.UserRepository;
import com.nexusmap.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PinsAndMeetupsTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PinRepository pinRepository;

    @Autowired
    private MeetupRepository meetupRepository;

    @Autowired
    private MeetupParticipantRepository participantRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    private User aliceUser;
    private User bobUser;
    private User adminUser;
    private String aliceToken;
    private String bobToken;
    private String adminToken;

    @BeforeEach
    void setUp() {
        participantRepository.deleteAll();
        meetupRepository.deleteAll();
        pinRepository.deleteAll();
        userRepository.deleteAll();

        aliceUser = userRepository.save(User.builder()
            .name("Alice Explorer")
            .email("alice@nexus.edu")
            .password(passwordEncoder.encode("SecretPass123!"))
            .role(Role.ROLE_USER)
            .ghostMode(false)
            .build());

        bobUser = userRepository.save(User.builder()
            .name("Bob Builder")
            .email("bob@nexus.edu")
            .password(passwordEncoder.encode("SecretPass123!"))
            .role(Role.ROLE_USER)
            .ghostMode(true)
            .build());

        adminUser = userRepository.save(User.builder()
            .name("System Admin")
            .email("admin@nexus.edu")
            .password(passwordEncoder.encode("AdminPass123!"))
            .role(Role.ROLE_ADMIN)
            .ghostMode(false)
            .build());

        aliceToken = jwtTokenProvider.generateToken(aliceUser);
        bobToken = jwtTokenProvider.generateToken(bobUser);
        adminToken = jwtTokenProvider.generateToken(adminUser);
    }

    @Test
    @DisplayName("TASK 1 & 3: Authenticated user can create a valid global pin")
    void shouldCreatePinSuccessfully() throws Exception {
        CreatePinRequest request = CreatePinRequest.builder()
            .title("Lost Hydro Flask")
            .description("Dark blue 32oz bottle left on 2nd floor library study desk.")
            .category(PinCategory.LOST_AND_FOUND)
            .latitude(37.7745)
            .longitude(-122.4200)
            .durationHours(48)
            .build();

        mockMvc.perform(post("/api/pins")
                .header("Authorization", "Bearer " + aliceToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").isNumber())
            .andExpect(jsonPath("$.title").value("Lost Hydro Flask"))
            .andExpect(jsonPath("$.category").value("LOST_AND_FOUND"))
            .andExpect(jsonPath("$.latitude").value(37.7745))
            .andExpect(jsonPath("$.longitude").value(-122.4200))
            .andExpect(jsonPath("$.creatorId").value(aliceUser.getId()))
            .andExpect(jsonPath("$.creatorName").value("Alice Explorer"))
            .andExpect(jsonPath("$.status").value("ACTIVE"))
            .andExpect(jsonPath("$.isCreator").value(true));

        assertThat(pinRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("TASK 1 & 7: Reject pin creation with invalid coordinates")
    void shouldRejectInvalidCoordinates() throws Exception {
        CreatePinRequest badLat = CreatePinRequest.builder()
            .title("Impossible Location")
            .description("Latitude beyond boundary limits")
            .category(PinCategory.GENERAL_ANNOUNCEMENT)
            .latitude(95.5) // Invalid > 90.0
            .longitude(-122.4200)
            .build();

        mockMvc.perform(post("/api/pins")
                .header("Authorization", "Bearer " + aliceToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(badLat)))
            .andExpect(status().isBadRequest());

        CreatePinRequest badLng = CreatePinRequest.builder()
            .title("Impossible Longitude")
            .description("Longitude beyond boundary limits")
            .category(PinCategory.GENERAL_ANNOUNCEMENT)
            .latitude(37.7745)
            .longitude(-185.0) // Invalid < -180.0
            .build();

        mockMvc.perform(post("/api/pins")
                .header("Authorization", "Bearer " + aliceToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(badLng)))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("TASK 3: Discover pins near a geographic coordinate within radius")
    void shouldDiscoverNearbyPinsWithinRadius() throws Exception {
        // Pin 1: Near Central Library (~100m away)
        CreatePinRequest libraryPin = CreatePinRequest.builder()
            .title("Library Study Session")
            .description("CS Algorithms prep group")
            .category(PinCategory.EVENT)
            .latitude(37.7745)
            .longitude(-122.4200)
            .build();

        // Pin 2: Near Student Canteen (~500m away)
        CreatePinRequest canteenPin = CreatePinRequest.builder()
            .title("Free Pizza Slice")
            .description("Surplus from orientation event")
            .category(PinCategory.FOOD)
            .latitude(37.7785)
            .longitude(-122.4200)
            .build();

        // Pin 3: Distant location (~15km away in Pacifica)
        CreatePinRequest distantPin = CreatePinRequest.builder()
            .title("Coastal Hike")
            .description("Weekend trail meet")
            .category(PinCategory.EVENT)
            .latitude(37.6200)
            .longitude(-122.4800)
            .build();

        mockMvc.perform(post("/api/pins")
                .header("Authorization", "Bearer " + aliceToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(libraryPin)))
            .andExpect(status().isCreated());

        mockMvc.perform(post("/api/pins")
                .header("Authorization", "Bearer " + aliceToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(canteenPin)))
            .andExpect(status().isCreated());

        mockMvc.perform(post("/api/pins")
                .header("Authorization", "Bearer " + aliceToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(distantPin)))
            .andExpect(status().isCreated());

        // Search near Library (37.7745, -122.4200) with 800m radius
        // Should find Library and Canteen, but exclude distant pin (15km)
        mockMvc.perform(get("/api/pins/nearby")
                .param("lat", "37.7745")
                .param("lng", "-122.4200")
                .param("radiusMeters", "800"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.pins", hasSize(2)))
            .andExpect(jsonPath("$.pins[0].title").value("Library Study Session"))
            .andExpect(jsonPath("$.pins[0].distanceMeters").value(lessThan(50.0)))
            .andExpect(jsonPath("$.pins[1].title").value("Free Pizza Slice"))
            .andExpect(jsonPath("$.pins[1].distanceMeters").value(greaterThan(400.0)));
    }

    @Test
    @DisplayName("TASK 3: Exclude expired pins from active search results")
    void shouldExcludeExpiredPinsFromNearbyResults() throws Exception {
        // Expired pin
        Pin expired = pinRepository.save(Pin.builder()
            .title("Old Meetup")
            .description("Expired session")
            .category(PinCategory.EVENT)
            .status(PinStatus.ACTIVE)
            .location(com.nexusmap.util.GeometryUtils.createPoint(37.7745, -122.4200))
            .latitude(37.7745)
            .longitude(-122.4200)
            .creator(aliceUser)
            .expiresAt(Instant.now().minus(Duration.ofHours(2))) // already expired
            .build());

        // Active pin
        Pin active = pinRepository.save(Pin.builder()
            .title("Active Session")
            .description("Currently ongoing")
            .category(PinCategory.EVENT)
            .status(PinStatus.ACTIVE)
            .location(com.nexusmap.util.GeometryUtils.createPoint(37.7746, -122.4201))
            .latitude(37.7746)
            .longitude(-122.4201)
            .creator(aliceUser)
            .expiresAt(Instant.now().plus(Duration.ofHours(2)))
            .build());

        mockMvc.perform(get("/api/pins/nearby")
                .param("lat", "37.7745")
                .param("lng", "-122.4200")
                .param("radiusMeters", "500"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.pins", hasSize(1)))
            .andExpect(jsonPath("$.pins[0].id").value(active.getId()))
            .andExpect(jsonPath("$.pins[0].title").value("Active Session"));
    }

    @Test
    @DisplayName("TASK 3 & 7: Ownership verification for editing and deleting pins")
    void shouldEnforceOwnershipForPinModifications() throws Exception {
        // Alice creates a pin
        CreatePinRequest req = CreatePinRequest.builder()
            .title("Alice's Pin")
            .description("Original description")
            .category(PinCategory.GENERAL_ANNOUNCEMENT)
            .latitude(37.7745)
            .longitude(-122.4200)
            .build();

        String res = mockMvc.perform(post("/api/pins")
                .header("Authorization", "Bearer " + aliceToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();

        Long pinId = objectMapper.readTree(res).get("id").asLong();

        // Bob tries to edit Alice's pin -> 403 Forbidden
        UpdatePinRequest updateReq = UpdatePinRequest.builder()
            .title("Hacked Title by Bob")
            .build();

        mockMvc.perform(patch("/api/pins/" + pinId)
                .header("Authorization", "Bearer " + bobToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateReq)))
            .andExpect(status().isForbidden());

        // Alice updates her own pin -> 200 OK
        UpdatePinRequest aliceUpdate = UpdatePinRequest.builder()
            .title("Alice's Updated Pin")
            .build();

        mockMvc.perform(patch("/api/pins/" + pinId)
                .header("Authorization", "Bearer " + aliceToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(aliceUpdate)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.title").value("Alice's Updated Pin"));

        // Admin can delete Alice's pin -> 204 No Content
        mockMvc.perform(delete("/api/pins/" + pinId)
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isNoContent());

        // Verify pin status is now DELETED
        Pin deletedPin = pinRepository.findById(pinId).orElseThrow();
        assertThat(deletedPin.getStatus()).isEqualTo(PinStatus.DELETED);
    }

    @Test
    @DisplayName("TASK 4: Create meetup, register attendees, enforce capacity and prevent duplicates")
    void shouldManageMeetupLifecycleAndCapacity() throws Exception {
        // Alice creates a meetup with capacity = 2 (Alice auto-joins, so 1 spot left)
        CreatePinRequest meetupPin = CreatePinRequest.builder()
            .title("React Workshop")
            .description("Hands-on Next.js 14 session")
            .category(PinCategory.MEETUP)
            .latitude(37.7775)
            .longitude(-122.4145)
            .meetupDetails(CreateMeetupDetailsDto.builder()
                .startTime(Instant.now().plus(Duration.ofHours(3)))
                .endTime(Instant.now().plus(Duration.ofHours(5)))
                .capacity(2)
                .build())
            .build();

        String res = mockMvc.perform(post("/api/pins")
                .header("Authorization", "Bearer " + aliceToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(meetupPin)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.meetup").isMap())
            .andExpect(jsonPath("$.meetup.capacity").value(2))
            .andExpect(jsonPath("$.meetup.currentParticipants").value(1))
            .andExpect(jsonPath("$.meetup.isJoined").value(true))
            .andExpect(jsonPath("$.meetup.isFull").value(false))
            .andReturn().getResponse().getContentAsString();

        Long pinId = objectMapper.readTree(res).get("id").asLong();
        Long meetupId = objectMapper.readTree(res).get("meetup").get("id").asLong();

        // Alice tries to join again -> 409 Conflict (duplicate prevention)
        mockMvc.perform(post("/api/pins/" + pinId + "/join")
                .header("Authorization", "Bearer " + aliceToken))
            .andExpect(status().isConflict());

        // Bob joins the meetup -> 200 OK (Capacity now reaches 2/2 -> full)
        mockMvc.perform(post("/api/pins/" + pinId + "/join")
                .header("Authorization", "Bearer " + bobToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.currentParticipants").value(2))
            .andExpect(jsonPath("$.isFull").value(true))
            .andExpect(jsonPath("$.isJoined").value(true));

        // Admin tries to join full meetup -> 400 Bad Request (capacity enforcement)
        mockMvc.perform(post("/api/pins/" + pinId + "/join")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isBadRequest());

        // Bob leaves the meetup -> 200 OK (Capacity now 1/2 -> no longer full)
        mockMvc.perform(post("/api/pins/" + pinId + "/leave")
                .header("Authorization", "Bearer " + bobToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.currentParticipants").value(1))
            .andExpect(jsonPath("$.isFull").value(false))
            .andExpect(jsonPath("$.isJoined").value(false));

        // Now Admin can join -> 200 OK
        mockMvc.perform(post("/api/meetups/" + meetupId + "/join")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.currentParticipants").value(2))
            .andExpect(jsonPath("$.isFull").value(true));
    }

    @Test
    @DisplayName("TASK 7: Ghost Mode creators have their identity masked on pins")
    void shouldMaskGhostModeCreatorIdentity() throws Exception {
        // Bob is in Ghost Mode
        CreatePinRequest ghostPin = CreatePinRequest.builder()
            .title("Anonymous Campus Alert")
            .description("Spotted keycard reader malfunctioning at North Gate")
            .category(PinCategory.CAMPUS_ISSUE)
            .latitude(37.7710)
            .longitude(-122.4245)
            .build();

        String res = mockMvc.perform(post("/api/pins")
                .header("Authorization", "Bearer " + bobToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(ghostPin)))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();

        Long pinId = objectMapper.readTree(res).get("id").asLong();

        // Alice views the pin created by Ghost Mode Bob -> identity masked
        mockMvc.perform(get("/api/pins/" + pinId)
                .header("Authorization", "Bearer " + aliceToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.creatorName").value("Ghost Operator"))
            .andExpect(jsonPath("$.creatorId").doesNotExist())
            .andExpect(jsonPath("$.isCreator").value(false));

        // Bob views his own pin -> reveals he is the creator
        mockMvc.perform(get("/api/pins/" + pinId)
                .header("Authorization", "Bearer " + bobToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.isCreator").value(true));
    }

    @Test
    @DisplayName("TASK 7: Rate limiting enforces maximum 5 pins per 10 minutes")
    void shouldEnforcePinCreationRateLimit() throws Exception {
        for (int i = 1; i <= 5; i++) {
            CreatePinRequest pin = CreatePinRequest.builder()
                .title("Spam Pin #" + i)
                .description("Rapid pin submission test")
                .category(PinCategory.GENERAL_ANNOUNCEMENT)
                .latitude(37.7745)
                .longitude(-122.4200)
                .build();

            mockMvc.perform(post("/api/pins")
                    .header("Authorization", "Bearer " + aliceToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(pin)))
                .andExpect(status().isCreated());
        }

        // 6th pin within 10 minutes -> 429 Too Many Requests
        CreatePinRequest excessPin = CreatePinRequest.builder()
            .title("Spam Pin #6")
            .description("Should be blocked by rate limit")
            .category(PinCategory.GENERAL_ANNOUNCEMENT)
            .latitude(37.7745)
            .longitude(-122.4200)
            .build();

        mockMvc.perform(post("/api/pins")
                .header("Authorization", "Bearer " + aliceToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(excessPin)))
            .andExpect(status().is(429));
    }
}
