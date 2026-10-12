package com.nexusmap;

import com.nexusmap.dto.NearbyZoneResponseDto;
import com.nexusmap.model.Zone;
import com.nexusmap.model.ZoneType;
import com.nexusmap.repository.UserRepository;
import com.nexusmap.repository.ZoneMembershipRepository;
import com.nexusmap.repository.ZoneRepository;
import com.nexusmap.service.LocationPrivacyService;
import com.nexusmap.service.SpatialEngineService;
import com.nexusmap.util.GeometryUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SpatialEngineServiceTest {

    @Mock
    private ZoneRepository zoneRepository;

    @Mock
    private ZoneMembershipRepository zoneMembershipRepository;

    @Mock
    private UserRepository userRepository;

    private SpatialEngineService spatialEngineService;

    private Zone perimeterZone;
    private Zone libraryZone;

    @BeforeEach
    void setUp() {
        LocationPrivacyService locationPrivacyService = new LocationPrivacyService("test-spatial-engine-secret");
        spatialEngineService = new SpatialEngineService(zoneRepository, zoneMembershipRepository, userRepository, locationPrivacyService);

        // Campus Perimeter: [37.7680, -122.4300] to [37.7840, -122.4080]
        perimeterZone = Zone.builder()
            .id(1L)
            .name("Campus Perimeter Boundary")
            .code("ZONE-PERIMETER")
            .zoneType(ZoneType.CAMPUS_PERIMETER)
            .active(true)
            .centerLat(37.7750)
            .centerLng(-122.4190)
            .boundary(GeometryUtils.createPolygon(List.of(
                List.of(-122.4300, 37.7680),
                List.of(-122.4080, 37.7680),
                List.of(-122.4080, 37.7840),
                List.of(-122.4300, 37.7840)
            )))
            .build();

        // Central Library: [37.7730, -122.4220] to [37.7760, -122.4180]
        libraryZone = Zone.builder()
            .id(2L)
            .name("Central Academic Library")
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
            .build();
    }

    @Test
    @DisplayName("TASK 3: Should return true when point is strictly inside polygon boundary")
    void shouldDetectPointInsidePolygon() {
        when(zoneRepository.findByActiveTrue()).thenReturn(List.of(perimeterZone, libraryZone));

        // Center of library: (37.7745, -122.4200)
        boolean inside = spatialEngineService.isPointInsideCampus(37.7745, -122.4200);
        assertThat(inside).isTrue();
    }

    @Test
    @DisplayName("TASK 3: Should return false when point is outside campus polygon")
    void shouldDetectPointOutsidePolygon() {
        when(zoneRepository.findByActiveTrue()).thenReturn(List.of(perimeterZone, libraryZone));

        // Far outside campus: (37.8000, -122.5000)
        boolean inside = spatialEngineService.isPointInsideCampus(37.8000, -122.5000);
        assertThat(inside).isFalse();
    }

    @Test
    @DisplayName("TASK 3 & 8: Should detect point exactly on polygon boundary line (covers)")
    void shouldDetectPointOnPolygonBoundary() {
        when(zoneRepository.findByActiveTrue()).thenReturn(List.of(libraryZone));

        // Point exactly on south edge of library: lat 37.7730, lng -122.4200
        List<Zone> containing = spatialEngineService.findZonesContainingPoint(37.7730, -122.4200);
        assertThat(containing).isNotEmpty();
        assertThat(containing.get(0).getCode()).isEqualTo("ZONE-LIBRARY");
    }

    @Test
    @DisplayName("TASK 3 & 8: Should handle overlapping zones (Campus Perimeter + Library Facility)")
    void shouldHandleOverlappingZonesWithProperPriority() {
        when(zoneRepository.findByActiveTrue()).thenReturn(List.of(perimeterZone, libraryZone));

        // Inside Library which is also inside Perimeter
        List<Zone> containing = spatialEngineService.findZonesContainingPoint(37.7745, -122.4200);
        assertThat(containing).hasSize(2);
        // Specific ACADEMIC facility should be sorted before general CAMPUS_PERIMETER
        assertThat(containing.get(0).getCode()).isEqualTo("ZONE-LIBRARY");
        assertThat(containing.get(1).getCode()).isEqualTo("ZONE-PERIMETER");
    }

    @Test
    @DisplayName("TASK 3 & 8: Should calculate distance and find nearby zones within radius")
    void shouldFindZonesWithinDistanceRadius() {
        when(zoneRepository.findByActiveTrue()).thenReturn(List.of(libraryZone));
        when(zoneMembershipRepository.countByZoneAndActiveTrue(libraryZone)).thenReturn(5L);

        // Point 100 meters away from library center
        List<NearbyZoneResponseDto> nearby = spatialEngineService.findZonesNearby(37.7745, -122.4190, 500.0);
        assertThat(nearby).isNotEmpty();
        assertThat(nearby.get(0).zone().code()).isEqualTo("ZONE-LIBRARY");
        assertThat(nearby.get(0).distanceMeters()).isLessThan(500.0);
    }

    @Test
    @DisplayName("TASK 5 & 8: Should reject invalid latitude and longitude values")
    void shouldRejectInvalidCoordinates() {
        assertThatThrownBy(() -> spatialEngineService.isPointInsideCampus(95.0, -122.0))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Invalid latitude");

        assertThatThrownBy(() -> spatialEngineService.isPointInsideCampus(37.0, 195.0))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Invalid longitude");

        assertThatThrownBy(() -> spatialEngineService.isPointInsideCampus(Double.NaN, -122.0))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
