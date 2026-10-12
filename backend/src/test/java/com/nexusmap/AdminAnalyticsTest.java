package com.nexusmap;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexusmap.model.*;
import com.nexusmap.repository.*;
import com.nexusmap.security.JwtTokenProvider;
import com.nexusmap.util.GeometryUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminAnalyticsTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserLocationRepository userLocationRepository;

    @Autowired
    private ZoneRepository zoneRepository;

    @Autowired
    private ZoneMembershipRepository zoneMembershipRepository;

    @Autowired
    private PinRepository pinRepository;

    @Autowired
    private MeetupRepository meetupRepository;

    @Autowired
    private ChatMessageRepository chatMessageRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User regularUser;
    private User adminUser;
    private String userToken;
    private String adminToken;
    private Zone testZoneA;
    private Zone testZoneB;

    @BeforeEach
    void setUp() {
        chatMessageRepository.deleteAll();
        meetupRepository.deleteAll();
        pinRepository.deleteAll();
        zoneMembershipRepository.deleteAll();
        userLocationRepository.deleteAll();
        userRepository.deleteAll();

        regularUser = userRepository.save(User.builder()
            .name("Student User")
            .email("student@nexus.edu")
            .password(passwordEncoder.encode("Password123!"))
            .role(Role.ROLE_USER)
            .ghostMode(false)
            .locationSharingEnabled(true)
            .build());

        adminUser = userRepository.save(User.builder()
            .name("Campus Administrator")
            .email("admin@nexus.edu")
            .password(passwordEncoder.encode("AdminPass123!"))
            .role(Role.ROLE_ADMIN)
            .ghostMode(false)
            .locationSharingEnabled(true)
            .build());

        userToken = jwtTokenProvider.generateToken(regularUser);
        adminToken = jwtTokenProvider.generateToken(adminUser);

        testZoneA = zoneRepository.findByCode("ZONE-LIBRARY").orElseGet(() ->
            zoneRepository.save(Zone.builder()
                .name("Academic Library")
                .code("ZONE-LIBRARY")
                .zoneType(ZoneType.ACADEMIC)
                .boundary(GeometryUtils.createPolygon(new Coordinate[]{
                    new Coordinate(-122.4215, 37.7735),
                    new Coordinate(-122.4185, 37.7735),
                    new Coordinate(-122.4185, 37.7755),
                    new Coordinate(-122.4215, 37.7755),
                    new Coordinate(-122.4215, 37.7735)
                }))
                .centerLat(37.7745)
                .centerLng(-122.4200)
                .active(true)
                .build())
        );

        testZoneB = zoneRepository.findByCode("ZONE-CANTEEN").orElseGet(() ->
            zoneRepository.save(Zone.builder()
                .name("Student Canteen")
                .code("ZONE-CANTEEN")
                .zoneType(ZoneType.DINING)
                .boundary(GeometryUtils.createPolygon(new Coordinate[]{
                    new Coordinate(-122.4225, 37.7715),
                    new Coordinate(-122.4195, 37.7715),
                    new Coordinate(-122.4195, 37.7730),
                    new Coordinate(-122.4225, 37.7730),
                    new Coordinate(-122.4225, 37.7715)
                }))
                .centerLat(37.7722)
                .centerLng(-122.4210)
                .active(true)
                .build())
        );
    }

    @Test
    @DisplayName("TASK 1: Should reject unauthenticated and non-admin requests with 401 and 403")
    void testSecurityEnforcementOnAdminEndpoints() throws Exception {
        // 1. Unauthenticated -> 401 Unauthorized
        mockMvc.perform(get("/api/admin/analytics/overview"))
            .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/admin/analytics/zones"))
            .andExpect(status().isUnauthorized());

        // 2. Regular User (ROLE_USER) -> 403 Forbidden
        mockMvc.perform(get("/api/admin/analytics/overview")
                .header("Authorization", "Bearer " + userToken))
            .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/admin/analytics/zones")
                .header("Authorization", "Bearer " + userToken))
            .andExpect(status().isForbidden());

        // 3. Admin User (ROLE_ADMIN) -> 200 OK
        mockMvc.perform(get("/api/admin/analytics/overview")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("TASK 2 & 4: Overview analytics should calculate accurate counts and definitions")
    void testOverviewAnalyticsCalculation() throws Exception {
        Instant now = Instant.now();

        // Seed 1 active pin
        Pin activePin = pinRepository.save(Pin.builder()
            .title("Hackathon Jam")
            .description("Collaborative coding sprint")
            .category(PinCategory.EVENT)
            .location(GeometryUtils.createPoint(37.7745, -122.4200))
            .latitude(37.7745)
            .longitude(-122.4200)
            .creator(adminUser)
            .status(PinStatus.ACTIVE)
            .expiresAt(now.plus(2, ChronoUnit.HOURS))
            .build());

        // Seed 1 active meetup
        meetupRepository.save(Meetup.builder()
            .pin(activePin)
            .startTime(now.plus(1, ChronoUnit.HOURS))
            .endTime(now.plus(3, ChronoUnit.HOURS))
            .active(true)
            .build());

        // Seed 2 chat messages
        chatMessageRepository.save(ChatMessage.builder()
            .sender(regularUser)
            .zone(testZoneA)
            .content("Hello Library!")
            .build());

        chatMessageRepository.save(ChatMessage.builder()
            .sender(adminUser)
            .zone(testZoneA)
            .content("Welcome to NEXUSMAP.")
            .build());

        mockMvc.perform(get("/api/admin/analytics/overview")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalRegisteredUsers").value(2))
            .andExpect(jsonPath("$.activePinsCount").value(1))
            .andExpect(jsonPath("$.activeMeetupsCount").value(1))
            .andExpect(jsonPath("$.totalMessagesCount").value(2))
            .andExpect(jsonPath("$.metricDefinitions").exists())
            .andExpect(jsonPath("$.metricDefinitions.densityHeatmap").exists());
    }

    @Test
    @DisplayName("TASK 2 & 4: Zone occupancy analytics should report active and privacy-eligible members")
    void testZoneOccupancyAnalytics() throws Exception {
        // User 1 in Library (sharing ON, ghost OFF)
        zoneMembershipRepository.save(ZoneMembership.builder().user(regularUser).zone(testZoneA).active(true).build());

        // User 2 in Library (ghost ON)
        User ghostUser = userRepository.save(User.builder()
            .name("Ghost Peer")
            .email("ghost@nexus.edu")
            .password(passwordEncoder.encode("Pass123!"))
            .role(Role.ROLE_USER)
            .ghostMode(true)
            .locationSharingEnabled(true)
            .build());
        zoneMembershipRepository.save(ZoneMembership.builder().user(ghostUser).zone(testZoneA).active(true).build());

        String json = mockMvc.perform(get("/api/admin/analytics/zones")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        // In testZoneA: total active members = 2, privacy-eligible = 1 (ghostUser excluded!)
        mockMvc.perform(get("/api/admin/analytics/zones")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.zoneCode == 'ZONE-LIBRARY')].activeMembers").value(2))
            .andExpect(jsonPath("$[?(@.zoneCode == 'ZONE-LIBRARY')].privacyEligibleMembers").value(1));
    }

    @Test
    @DisplayName("TASK 3 & 4: Density heatmap must enforce minimum k-anonymity threshold")
    void testDensityHeatmapPrivacyThresholdEnforcement() throws Exception {
        // Seed 4 sharing users in testZoneA (meets minThreshold of 3)
        for (int i = 1; i <= 4; i++) {
            User u = userRepository.save(User.builder()
                .name("Library Student " + i)
                .email("lib" + i + "@nexus.edu")
                .password(passwordEncoder.encode("Pass123!"))
                .role(Role.ROLE_USER)
                .ghostMode(false)
                .locationSharingEnabled(true)
                .build());
            zoneMembershipRepository.save(ZoneMembership.builder().user(u).zone(testZoneA).active(true).build());
        }

        // Seed only 1 sharing user in testZoneB (fails minThreshold of 3, must be SUPPRESSED)
        User solitaryUser = userRepository.save(User.builder()
            .name("Solitary Canteen Diner")
            .email("solo@nexus.edu")
            .password(passwordEncoder.encode("Pass123!"))
            .role(Role.ROLE_USER)
            .ghostMode(false)
            .locationSharingEnabled(true)
            .build());
        zoneMembershipRepository.save(ZoneMembership.builder().user(solitaryUser).zone(testZoneB).active(true).build());

        // Query with minThreshold = 3
        mockMvc.perform(get("/api/admin/analytics/density")
                .param("minThreshold", "3")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.minThreshold").value(3))
            // Library meets threshold -> rendered
            .andExpect(jsonPath("$.densityPoints[?(@.zoneCode == 'ZONE-LIBRARY')]").exists())
            .andExpect(jsonPath("$.densityPoints[?(@.zoneCode == 'ZONE-LIBRARY')].userCount").value(4))
            // Canteen has only 1 user (< 3) -> SUPPRESSED from heatmap!
            .andExpect(jsonPath("$.densityPoints[?(@.zoneCode == 'ZONE-CANTEEN')]").doesNotExist())
            // Suppressed area counter incremented
            .andExpect(jsonPath("$.suppressedAreasCount").value(1));
    }

    @Test
    @DisplayName("TASK 4: Activity trends endpoint should accept intervals (24h, 7d, 30d)")
    void testActivityTrendsIntervals() throws Exception {
        // 24h default (24 hourly buckets)
        mockMvc.perform(get("/api/admin/analytics/activity")
                .param("interval", "24h")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.interval").value("24h"))
            .andExpect(jsonPath("$.buckets.length()").value(24));

        // 7d (7 daily buckets)
        mockMvc.perform(get("/api/admin/analytics/activity")
                .param("interval", "7d")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.interval").value("7d"))
            .andExpect(jsonPath("$.buckets.length()").value(7));

        // 30d (30 daily buckets)
        mockMvc.perform(get("/api/admin/analytics/activity")
                .param("interval", "30d")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.interval").value("30d"))
            .andExpect(jsonPath("$.buckets.length()").value(30));
    }
}
