package com.nexusmap;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexusmap.dto.LocationUpdateRequest;
import com.nexusmap.dto.UpdatePrivacySettingsRequest;
import com.nexusmap.model.Role;
import com.nexusmap.model.User;
import com.nexusmap.model.UserLocation;
import com.nexusmap.model.Zone;
import com.nexusmap.model.ZoneMembership;
import com.nexusmap.repository.UserLocationRepository;
import com.nexusmap.repository.UserRepository;
import com.nexusmap.repository.ZoneMembershipRepository;
import com.nexusmap.repository.ZoneRepository;
import com.nexusmap.security.JwtTokenProvider;
import com.nexusmap.service.LocationDataRetentionService;
import com.nexusmap.service.LocationPrivacyService;
import com.nexusmap.util.GeometryUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PrivacyAndGhostModeTest {

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
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private LocationPrivacyService locationPrivacyService;

    @Autowired
    private LocationDataRetentionService locationDataRetentionService;

    private User alice;
    private User bob;
    private User charlie;
    private String aliceToken;
    private String bobToken;
    private Zone testZone;

    @BeforeEach
    void setUp() {
        zoneMembershipRepository.deleteAll();
        userLocationRepository.deleteAll();
        userRepository.deleteAll();

        alice = userRepository.save(User.builder()
            .name("Alice Security")
            .email("alice.privacy@campus.edu")
            .password(passwordEncoder.encode("SecurePass123!"))
            .role(Role.ROLE_USER)
            .ghostMode(false)
            .locationSharingEnabled(true)
            .fuzzingRadiusMeters(100)
            .build());

        bob = userRepository.save(User.builder()
            .name("Bob Operator")
            .email("bob.privacy@campus.edu")
            .password(passwordEncoder.encode("SecurePass123!"))
            .role(Role.ROLE_USER)
            .ghostMode(false)
            .locationSharingEnabled(true)
            .fuzzingRadiusMeters(100)
            .build());

        charlie = userRepository.save(User.builder()
            .name("Charlie Student")
            .email("charlie.privacy@campus.edu")
            .password(passwordEncoder.encode("SecurePass123!"))
            .role(Role.ROLE_USER)
            .ghostMode(false)
            .locationSharingEnabled(true)
            .fuzzingRadiusMeters(100)
            .build());

        aliceToken = jwtTokenProvider.generateToken(alice);
        bobToken = jwtTokenProvider.generateToken(bob);

        testZone = zoneRepository.findByCode("ZONE-MAIN-GATE").orElseGet(() ->
            zoneRepository.save(Zone.builder()
                .name("Main Campus Gate")
                .code("ZONE-MAIN-GATE")
                .description("Entrance and perimeter gateway")
                .zoneType(com.nexusmap.model.ZoneType.ACADEMIC)
                .boundary(GeometryUtils.createPolygon(new Coordinate[]{
                    new Coordinate(-122.4260, 37.7700),
                    new Coordinate(-122.4230, 37.7700),
                    new Coordinate(-122.4230, 37.7725),
                    new Coordinate(-122.4260, 37.7725),
                    new Coordinate(-122.4260, 37.7700)
                }))
                .centerLat(37.7712)
                .centerLng(-122.4245)
                .active(true)
                .build())
        );
    }

    @Test
    @DisplayName("TASK 1 & 4: Should update privacy settings and toggle Ghost Mode")
    void testToggleGhostModeAndPrivacySettings() throws Exception {
        // Initial state: ghostMode=false, sharing=true
        mockMvc.perform(get("/api/privacy/settings")
                .header("Authorization", "Bearer " + aliceToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.ghostMode").value(false))
            .andExpect(jsonPath("$.locationSharingEnabled").value(true))
            .andExpect(jsonPath("$.fuzzingRadiusMeters").value(100));

        // Enable Ghost Mode
        UpdatePrivacySettingsRequest updateReq = new UpdatePrivacySettingsRequest(true, null, 150);
        mockMvc.perform(put("/api/privacy/settings")
                .header("Authorization", "Bearer " + aliceToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateReq)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.ghostMode").value(true))
            .andExpect(jsonPath("$.locationSharingEnabled").value(true))
            .andExpect(jsonPath("$.fuzzingRadiusMeters").value(150));

        User updatedAlice = userRepository.findById(alice.getId()).orElseThrow();
        assertThat(updatedAlice.isGhostMode()).isTrue();
        assertThat(updatedAlice.getFuzzingRadiusMeters()).isEqualTo(150);
    }

    @Test
    @DisplayName("TASK 4: Unauthorized user cannot view or modify privacy settings")
    void testUnauthorizedPrivacyAccessRejected() throws Exception {
        mockMvc.perform(get("/api/privacy/settings"))
            .andExpect(status().isUnauthorized());

        UpdatePrivacySettingsRequest updateReq = new UpdatePrivacySettingsRequest(true, false, 100);
        mockMvc.perform(put("/api/privacy/settings")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateReq)))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("TASK 1, 4 & 5: Ghost Mode and Disabled Sharing exclude users from Peer Locations")
    void testGhostModeAndDisabledSharingExcludeFromPeerDiscovery() throws Exception {
        // Setup Bob and Alice inside testZone
        zoneMembershipRepository.save(ZoneMembership.builder().user(alice).zone(testZone).active(true).build());
        zoneMembershipRepository.save(ZoneMembership.builder().user(bob).zone(testZone).active(true).build());

        // Record recent location for Bob: (37.7710, -122.4245)
        userLocationRepository.save(UserLocation.builder()
            .user(bob)
            .location(GeometryUtils.createPoint(37.7710, -122.4245))
            .latitude(37.7710)
            .longitude(-122.4245)
            .accuracy(5.0)
            .build());

        // 1. Bob has Ghost Mode OFF, sharing ON -> Alice can see Bob
        mockMvc.perform(get("/api/privacy/peers")
                .param("zoneId", testZone.getId().toString())
                .header("Authorization", "Bearer " + aliceToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].displayName").value("Bob Operator"));

        // 2. Bob enables Ghost Mode
        bob.setGhostMode(true);
        userRepository.save(bob);

        // Alice queries peers -> Bob is completely omitted
        mockMvc.perform(get("/api/privacy/peers")
                .param("zoneId", testZone.getId().toString())
                .header("Authorization", "Bearer " + aliceToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(0));

        // 3. Bob disables Ghost Mode, but disables location sharing
        bob.setGhostMode(false);
        bob.setLocationSharingEnabled(false);
        userRepository.save(bob);

        // Alice queries peers -> Bob is still omitted
        mockMvc.perform(get("/api/privacy/peers")
                .param("zoneId", testZone.getId().toString())
                .header("Authorization", "Bearer " + aliceToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("TASK 2: Server-side coordinate fuzzing never returns raw GPS coordinates")
    void testServerSideCoordinateFuzzing_NeverReturnsRawGPS() throws Exception {
        double rawLat = 37.771000;
        double rawLng = -122.424500;

        zoneMembershipRepository.save(ZoneMembership.builder().user(alice).zone(testZone).active(true).build());
        zoneMembershipRepository.save(ZoneMembership.builder().user(bob).zone(testZone).active(true).build());

        userLocationRepository.save(UserLocation.builder()
            .user(bob)
            .location(GeometryUtils.createPoint(rawLat, rawLng))
            .latitude(rawLat)
            .longitude(rawLng)
            .accuracy(5.0)
            .build());

        String responseJson = mockMvc.perform(get("/api/privacy/peers")
                .param("zoneId", testZone.getId().toString())
                .header("Authorization", "Bearer " + aliceToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andReturn().getResponse().getContentAsString();

        double fuzzedLat = objectMapper.readTree(responseJson).get(0).get("latitude").asDouble();
        double fuzzedLng = objectMapper.readTree(responseJson).get(0).get("longitude").asDouble();

        // 1. Raw coordinates must NOT match fuzzed coordinates
        assertThat(fuzzedLat).isNotEqualTo(rawLat);
        assertThat(fuzzedLng).isNotEqualTo(rawLng);

        // 2. Distance between raw and fuzzed must satisfy minimum offset threshold (>= 30m)
        double distanceMeters = GeometryUtils.calculateDistanceMeters(rawLat, rawLng, fuzzedLat, fuzzedLng);
        assertThat(distanceMeters).isGreaterThanOrEqualTo(30.0);
        // And stay within reasonable privacy radius bound (~400m)
        assertThat(distanceMeters).isLessThan(500.0);
    }

    @Test
    @DisplayName("TASK 2: Anti-averaging protection — repeated queries yield zero sample variance")
    void testAntiAveragingProtection_ZeroVarianceAcrossRepeatedQueries() {
        double rawLat = 37.771000;
        double rawLng = -122.424500;

        // Perform 50 repeated fuzz calculations within the same time epoch
        Set<Coordinate> distinctOutputs = new HashSet<>();
        for (int i = 0; i < 50; i++) {
            Coordinate fuzzed = locationPrivacyService.computeAntiAveragingFuzz(bob.getId(), rawLat, rawLng, 100);
            distinctOutputs.add(fuzzed);
        }

        // Must produce EXACTLY 1 unique output across all 50 queries: sample variance = 0!
        assertThat(distinctOutputs).hasSize(1);
    }

    @Test
    @DisplayName("TASK 2: Distinct users at the same raw coordinates receive distinct perturbation offsets")
    void testDistinctUsersReceiveDistinctPerturbations() {
        double rawLat = 37.771000;
        double rawLng = -122.424500;

        Coordinate fuzzedBob = locationPrivacyService.computeAntiAveragingFuzz(bob.getId(), rawLat, rawLng, 100);
        Coordinate fuzzedCharlie = locationPrivacyService.computeAntiAveragingFuzz(charlie.getId(), rawLat, rawLng, 100);

        assertThat(fuzzedBob.x).isNotEqualTo(fuzzedCharlie.x);
        assertThat(fuzzedBob.y).isNotEqualTo(fuzzedCharlie.y);
    }

    @Test
    @DisplayName("TASK 3: Ephemeral data retention policy prunes stale location trails")
    void testEphemeralLocationDataRetentionPruning() {
        Instant now = Instant.now();

        // 1. Stale location recorded 45 minutes ago
        userLocationRepository.save(UserLocation.builder()
            .user(bob)
            .location(GeometryUtils.createPoint(37.7710, -122.4245))
            .latitude(37.7710)
            .longitude(-122.4245)
            .recordedAt(now.minus(Duration.ofMinutes(45)))
            .build());

        // 2. Recent location recorded 5 minutes ago
        userLocationRepository.save(UserLocation.builder()
            .user(bob)
            .location(GeometryUtils.createPoint(37.7715, -122.4240))
            .latitude(37.7715)
            .longitude(-122.4240)
            .recordedAt(now.minus(Duration.ofMinutes(5)))
            .build());

        assertThat(userLocationRepository.count()).isEqualTo(2);

        // Run retention pruning (threshold: 30 minutes)
        int purged = locationDataRetentionService.purgeExpiredLocations(Duration.ofMinutes(30));
        assertThat(purged).isEqualTo(1);

        // Only the recent location remains
        assertThat(userLocationRepository.count()).isEqualTo(1);
        UserLocation remaining = userLocationRepository.findAll().get(0);
        assertThat(remaining.getLatitude()).isEqualTo(37.7715);
    }
}
