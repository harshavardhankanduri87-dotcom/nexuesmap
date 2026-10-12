package com.nexusmap;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexusmap.dto.LocationUpdateRequest;
import com.nexusmap.model.Role;
import com.nexusmap.model.TransitionType;
import com.nexusmap.model.User;
import com.nexusmap.repository.UserLocationRepository;
import com.nexusmap.repository.UserRepository;
import com.nexusmap.repository.ZoneMembershipRepository;
import com.nexusmap.repository.ZoneRepository;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class GeofencingIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ZoneRepository zoneRepository;

    @Autowired
    private ZoneMembershipRepository zoneMembershipRepository;

    @Autowired
    private UserLocationRepository userLocationRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private User testUser;
    private String userToken;

    @BeforeEach
    void setUp() {
        zoneMembershipRepository.deleteAll();
        userLocationRepository.deleteAll();
        userRepository.deleteAll();

        testUser = userRepository.save(User.builder()
            .name("Explorer Jane")
            .email("jane@campus.edu")
            .password(passwordEncoder.encode("SecurePass123!"))
            .role(Role.ROLE_USER)
            .ghostMode(false)
            .build());

        userToken = jwtTokenProvider.generateToken(testUser);
    }

    @Test
    @DisplayName("TASK 7 & 8: Should reject location updates without authentication (401 Unauthorized)")
    void shouldRejectUnauthenticatedLocationUpdate() throws Exception {
        LocationUpdateRequest request = new LocationUpdateRequest(37.7710, -122.4245, 10.0);

        mockMvc.perform(post("/api/location/update")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("TASK 5 & 8: Should reject location updates with invalid coordinate ranges (400 Bad Request)")
    void shouldRejectInvalidCoordinatesOnLocationUpdate() throws Exception {
        LocationUpdateRequest invalidLat = new LocationUpdateRequest(120.0, -122.4245, 10.0);

        mockMvc.perform(post("/api/location/update")
                .header("Authorization", "Bearer " + userToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidLat)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.validationErrors.latitude").exists());
    }

    @Test
    @DisplayName("TASK 4 & 8: Should process zone entry, maintenance, and duplicate membership prevention")
    void shouldHandleZoneEntryAndMaintainMembership() throws Exception {
        // Point inside Main Gate: lat 37.7710, lng -122.4245
        LocationUpdateRequest entryRequest = new LocationUpdateRequest(37.7710, -122.4245, 5.0);

        // 1. Initial Entry
        mockMvc.perform(post("/api/location/update")
                .header("Authorization", "Bearer " + userToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(entryRequest)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.transitionType").value("ENTERED"))
            .andExpect(jsonPath("$.currentZone.code").value("ZONE-MAIN-GATE"));

        assertThat(zoneMembershipRepository.countByZoneAndActiveTrue(
            zoneRepository.findByCode("ZONE-MAIN-GATE").orElseThrow()
        )).isEqualTo(1);

        // 2. Small movement within same zone (Maintained)
        LocationUpdateRequest maintainedRequest = new LocationUpdateRequest(37.7711, -122.4246, 5.0);

        mockMvc.perform(post("/api/location/update")
                .header("Authorization", "Bearer " + userToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(maintainedRequest)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.transitionType").value("MAINTAINED"))
            .andExpect(jsonPath("$.currentZone.code").value("ZONE-MAIN-GATE"));

        // Verify duplicate memberships are strictly prevented
        assertThat(zoneMembershipRepository.countByZoneAndActiveTrue(
            zoneRepository.findByCode("ZONE-MAIN-GATE").orElseThrow()
        )).isEqualTo(1);
    }

    @Test
    @DisplayName("TASK 4 & 8: Should handle zone-to-zone transitions and exit events correctly")
    void shouldHandleZoneTransitionsAndExit() throws Exception {
        // 1. Enter Main Gate
        LocationUpdateRequest gateReq = new LocationUpdateRequest(37.7710, -122.4245, 5.0);
        mockMvc.perform(post("/api/location/update")
                .header("Authorization", "Bearer " + userToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(gateReq)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.transitionType").value("ENTERED"))
            .andExpect(jsonPath("$.currentZone.code").value("ZONE-MAIN-GATE"));

        // 2. Move to Central Academic Library: lat 37.7745, lng -122.4200
        LocationUpdateRequest libraryReq = new LocationUpdateRequest(37.7745, -122.4200, 5.0);
        mockMvc.perform(post("/api/location/update")
                .header("Authorization", "Bearer " + userToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(libraryReq)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.transitionType").value("ENTERED"))
            .andExpect(jsonPath("$.currentZone.code").value("ZONE-LIBRARY"))
            .andExpect(jsonPath("$.previousZone.code").value("ZONE-MAIN-GATE"));

        // Verify Main Gate has 0 active members and Library has 1
        assertThat(zoneMembershipRepository.countByZoneAndActiveTrue(
            zoneRepository.findByCode("ZONE-MAIN-GATE").orElseThrow()
        )).isEqualTo(0);
        assertThat(zoneMembershipRepository.countByZoneAndActiveTrue(
            zoneRepository.findByCode("ZONE-LIBRARY").orElseThrow()
        )).isEqualTo(1);

        // 3. Move far outside all zones: lat 37.8100, lng -122.5000
        LocationUpdateRequest outsideReq = new LocationUpdateRequest(37.8100, -122.5000, 10.0);
        mockMvc.perform(post("/api/location/update")
                .header("Authorization", "Bearer " + userToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(outsideReq)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.transitionType").value("EXITED"))
            .andExpect(jsonPath("$.previousZone.code").value("ZONE-LIBRARY"));

        // Verify Library is now also 0 active members
        assertThat(zoneMembershipRepository.countByZoneAndActiveTrue(
            zoneRepository.findByCode("ZONE-LIBRARY").orElseThrow()
        )).isEqualTo(0);
    }

    @Test
    @DisplayName("TASK 5: Should retrieve list of all active campus zones")
    void shouldGetAllZones() throws Exception {
        mockMvc.perform(get("/api/zones"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(5))))
            .andExpect(jsonPath("$[?(@.code == 'ZONE-MAIN-GATE')]").exists())
            .andExpect(jsonPath("$[?(@.code == 'ZONE-LIBRARY')]").exists())
            .andExpect(jsonPath("$[0].leafletCoordinates").isArray())
            .andExpect(jsonPath("$[0].geoJsonCoordinates").isArray());
    }

    @Test
    @DisplayName("TASK 5: Should return campus contains status for points inside and outside")
    void shouldCheckCampusContains() throws Exception {
        // Inside campus
        mockMvc.perform(get("/api/campus/contains")
                .param("lat", "37.7745")
                .param("lng", "-122.4200"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.insideCampus").value(true));

        // Outside campus
        mockMvc.perform(get("/api/campus/contains")
                .param("lat", "37.8200")
                .param("lng", "-122.5200"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.insideCampus").value(false));
    }

    @Test
    @DisplayName("TASK 5: Should find nearby zones within distance radius")
    void shouldFindNearbyZones() throws Exception {
        // Query near library with 800m radius
        mockMvc.perform(get("/api/zones/nearby")
                .param("lat", "37.7745")
                .param("lng", "-122.4200")
                .param("radius", "800"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", not(empty())))
            .andExpect(jsonPath("$[0].zone").exists())
            .andExpect(jsonPath("$[0].distanceMeters").isNumber());
    }
}
