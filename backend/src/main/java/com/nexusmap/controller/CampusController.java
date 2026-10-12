package com.nexusmap.controller;

import com.nexusmap.dto.CampusContainsResponse;
import com.nexusmap.service.SpatialEngineService;
import com.nexusmap.util.GeometryUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/api/campus")
@RequiredArgsConstructor
public class CampusController {

    private final SpatialEngineService spatialEngineService;

    /**
     * GET /api/campus/contains - Determines whether given coordinates fall inside the campus perimeter.
     */
    @GetMapping("/contains")
    public ResponseEntity<CampusContainsResponse> checkCampusContains(
            @RequestParam double lat,
            @RequestParam double lng
    ) {
        GeometryUtils.validateCoordinates(lat, lng);
        boolean inside = spatialEngineService.isPointInsideCampus(lat, lng);
        int containingCount = spatialEngineService.findZonesContainingPoint(lat, lng).size();

        return ResponseEntity.ok(new CampusContainsResponse(
            inside,
            lat,
            lng,
            containingCount,
            Instant.now()
        ));
    }
}
