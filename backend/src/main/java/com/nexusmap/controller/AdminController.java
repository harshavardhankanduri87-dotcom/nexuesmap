package com.nexusmap.controller;

import com.nexusmap.dto.admin.ActivityTrendDto;
import com.nexusmap.dto.admin.AdminOverviewDto;
import com.nexusmap.dto.admin.DensityHeatmapDto;
import com.nexusmap.dto.admin.ZoneOccupancyDto;
import com.nexusmap.service.AdminAnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Controller providing administrative oversight, telemetry, and privacy-preserving density heatmaps.
 * Access is strictly restricted to authenticated users with the ROLE_ADMIN authority.
 */
@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminController {

    private final AdminAnalyticsService adminAnalyticsService;

    /**
     * GET /api/admin/metrics - Lightweight platform health and access level probe.
     */
    @GetMapping("/metrics")
    public ResponseEntity<Map<String, Object>> getAdminMetrics() {
        return ResponseEntity.ok(Map.of(
            "status", "AUTHORIZED",
            "accessLevel", "ADMINISTRATOR",
            "clusterTelemetry", "OPERATIONAL",
            "timestamp", Instant.now().toString()
        ));
    }

    /**
     * GET /api/admin/analytics/overview - Overall system KPIs.
     */
    @GetMapping("/analytics/overview")
    public ResponseEntity<AdminOverviewDto> getOverview() {
        return ResponseEntity.ok(adminAnalyticsService.getOverviewAnalytics());
    }

    /**
     * GET /api/admin/analytics/zones - Detailed zone-by-zone occupancy breakdown.
     */
    @GetMapping("/analytics/zones")
    public ResponseEntity<List<ZoneOccupancyDto>> getZoneOccupancy() {
        return ResponseEntity.ok(adminAnalyticsService.getZoneOccupancyAnalytics());
    }

    /**
     * GET /api/admin/analytics/activity - Activity trends over time.
     * Supported intervals: 24h (hourly buckets), 7d (daily buckets), 30d (daily buckets).
     */
    @GetMapping("/analytics/activity")
    public ResponseEntity<ActivityTrendDto> getActivityTrends(
            @RequestParam(defaultValue = "24h") String interval
    ) {
        return ResponseEntity.ok(adminAnalyticsService.getActivityTrends(interval));
    }

    /**
     * GET /api/admin/analytics/density - Privacy-preserving geographic density heatmap.
     * Enforces minimum k-anonymity threshold to suppress small populations and prevent de-anonymization.
     */
    @GetMapping("/analytics/density")
    public ResponseEntity<DensityHeatmapDto> getDensityHeatmap(
            @RequestParam(defaultValue = "3") int minThreshold
    ) {
        return ResponseEntity.ok(adminAnalyticsService.getDensityHeatmap(minThreshold));
    }
}
