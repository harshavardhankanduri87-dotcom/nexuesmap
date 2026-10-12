package com.nexusmap.service;

import com.nexusmap.dto.admin.ActivityTrendDto;
import com.nexusmap.dto.admin.AdminOverviewDto;
import com.nexusmap.dto.admin.DensityHeatmapDto;
import com.nexusmap.dto.admin.ZoneOccupancyDto;
import com.nexusmap.model.PinStatus;
import com.nexusmap.model.Zone;
import com.nexusmap.repository.*;
import com.nexusmap.util.GeometryUtils;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Service providing aggregated, privacy-preserving administrative analytics and geospatial heatmaps.
 *
 * Privacy Guarantees:
 * - Ghost Mode users and users with location sharing disabled are completely excluded from density observations.
 * - Minimum group threshold (k-anonymity) is strictly enforced: zones with headcounts below the threshold
 *   are suppressed from heatmaps to prevent de-anonymization of small groups or individuals.
 * - Zero raw coordinates, emails, or private messages are exposed in analytics payloads.
 */
@Service
@RequiredArgsConstructor
public class AdminAnalyticsService {

    private static final Logger log = LoggerFactory.getLogger(AdminAnalyticsService.class);

    private final UserRepository userRepository;
    private final UserLocationRepository userLocationRepository;
    private final ZoneRepository zoneRepository;
    private final ZoneMembershipRepository zoneMembershipRepository;
    private final PinRepository pinRepository;
    private final MeetupRepository meetupRepository;
    private final ChatMessageRepository chatMessageRepository;

    private static final DateTimeFormatter HOUR_FORMAT = DateTimeFormatter.ofPattern("HH:00").withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter DAY_FORMAT = DateTimeFormatter.ofPattern("MM/dd").withZone(ZoneOffset.UTC);

    private static final Map<String, String> METRIC_DEFINITIONS = Map.of(
        "totalRegisteredUsers", "Total user accounts registered in NEXUSMAP database.",
        "currentlyActiveUsers", "Unique authenticated accounts with recorded GPS telemetry or zone presence within the last 15 minutes.",
        "activeZonesCount", "Count of campus geofenced boundaries currently flagged active=true.",
        "activePinsCount", "Total map pins currently active and not past expiration timestamp.",
        "activeMeetupsCount", "Total scheduled collaborative meetups tied to active pins with future end times.",
        "totalMessagesCount", "Cumulative durable STOMP messages persisted across all campus zones.",
        "messagesInLast24Hours", "Messages dispatched across campus channels in the preceding 24-hour window.",
        "densityHeatmap", "Aggregated user headcount per campus zone enforcing a minimum k-anonymity privacy threshold."
    );

    /**
     * Aggregated system overview metrics.
     */
    @Transactional(readOnly = true)
    public AdminOverviewDto getOverviewAnalytics() {
        Instant now = Instant.now();
        Instant activeCutoff = now.minus(Duration.ofMinutes(15));
        Instant last24h = now.minus(Duration.ofHours(24));

        long totalUsers = userRepository.count();
        long activeUsers = userLocationRepository.countUniqueActiveUsersSince(activeCutoff);
        long activeZones = zoneRepository.countByActiveTrue();
        long activePins = pinRepository.countByStatusAndExpiresAtAfter(PinStatus.ACTIVE, now);
        long activeMeetups = meetupRepository.countActiveMeetups(now);
        long totalMessages = chatMessageRepository.count();
        long messages24h = chatMessageRepository.countByCreatedAtAfter(last24h);

        return new AdminOverviewDto(
            totalUsers,
            activeUsers,
            activeZones,
            activePins,
            activeMeetups,
            totalMessages,
            messages24h,
            now,
            METRIC_DEFINITIONS
        );
    }

    /**
     * Zone-by-zone occupancy breakdown.
     */
    @Transactional(readOnly = true)
    public List<ZoneOccupancyDto> getZoneOccupancyAnalytics() {
        List<Zone> activeZones = zoneRepository.findByActiveTrue();
        List<ZoneOccupancyDto> list = new ArrayList<>();

        for (Zone zone : activeZones) {
            long totalActive = zoneMembershipRepository.countByZoneAndActiveTrue(zone);
            long privacyEligible = zoneMembershipRepository.countPrivacyEligibleMembersByZone(zone);
            List<List<Double>> coords = GeometryUtils.toLeafletCoordinates(zone.getBoundary());

            list.add(new ZoneOccupancyDto(
                zone.getId(),
                zone.getName(),
                zone.getCode(),
                zone.getZoneType().name(),
                totalActive,
                privacyEligible,
                zone.getCenterLat(),
                zone.getCenterLng(),
                coords
            ));
        }

        list.sort(Comparator.comparingLong(ZoneOccupancyDto::activeMembers).reversed());
        return list;
    }

    /**
     * Time-series activity trends bucketed by interval.
     */
    @Transactional(readOnly = true)
    public ActivityTrendDto getActivityTrends(String interval) {
        String cleanInterval = (interval == null || interval.isBlank()) ? "24h" : interval.toLowerCase().trim();
        Instant now = Instant.now();

        int bucketCount;
        Duration bucketDuration;
        Duration totalSpan;
        DateTimeFormatter labelFormatter;

        switch (cleanInterval) {
            case "7d" -> {
                bucketCount = 7;
                bucketDuration = Duration.ofDays(1);
                totalSpan = Duration.ofDays(7);
                labelFormatter = DAY_FORMAT;
            }
            case "30d" -> {
                bucketCount = 30;
                bucketDuration = Duration.ofDays(1);
                totalSpan = Duration.ofDays(30);
                labelFormatter = DAY_FORMAT;
            }
            default -> {
                cleanInterval = "24h";
                bucketCount = 24;
                bucketDuration = Duration.ofHours(1);
                totalSpan = Duration.ofHours(24);
                labelFormatter = HOUR_FORMAT;
            }
        }

        Instant rangeStart = now.minus(totalSpan);
        List<ActivityTrendDto.ActivityBucketDto> buckets = new ArrayList<>();
        long sumMessages = 0;
        long sumLocations = 0;
        long sumPins = 0;

        for (int i = 0; i < bucketCount; i++) {
            Instant bStart = rangeStart.plus(bucketDuration.multipliedBy(i));
            Instant bEnd = bStart.plus(bucketDuration);
            if (bEnd.isAfter(now)) {
                bEnd = now;
            }

            long msgCount = chatMessageRepository.countByCreatedAtBetween(bStart, bEnd);
            long locCount = userLocationRepository.countByRecordedAtBetween(bStart, bEnd);
            long pinCount = pinRepository.countByCreatedAtBetween(bStart, bEnd);

            sumMessages += msgCount;
            sumLocations += locCount;
            sumPins += pinCount;

            buckets.add(new ActivityTrendDto.ActivityBucketDto(
                labelFormatter.format(bStart),
                bStart,
                bEnd,
                msgCount,
                locCount,
                pinCount
            ));
        }

        return new ActivityTrendDto(
            cleanInterval,
            rangeStart,
            now,
            sumMessages,
            sumLocations,
            sumPins,
            buckets
        );
    }

    /**
     * Privacy-preserving Geographic Density Heatmap.
     * Enforces strict minimum-group threshold (k-anonymity) and excludes Ghost Mode / non-sharing users.
     */
    @Transactional(readOnly = true)
    public DensityHeatmapDto getDensityHeatmap(int minThreshold) {
        int threshold = Math.max(1, Math.min(minThreshold, 50));
        List<Zone> activeZones = zoneRepository.findByActiveTrue();

        long totalObserved = 0;
        long privacyEligibleTotal = 0;
        int suppressedCount = 0;
        List<DensityHeatmapDto.ZoneDensityPointDto> points = new ArrayList<>();

        for (Zone zone : activeZones) {
            long totalActive = zoneMembershipRepository.countByZoneAndActiveTrue(zone);
            long privacyEligible = zoneMembershipRepository.countPrivacyEligibleMembersByZone(zone);

            totalObserved += totalActive;
            privacyEligibleTotal += privacyEligible;

            // Privacy Filter: enforce minimum aggregation threshold
            if (privacyEligible < threshold) {
                if (privacyEligible > 0) {
                    suppressedCount++;
                }
                // Suppressed from public heatmap layer to prevent identifying solitary users
                continue;
            }

            double intensity = Math.min(1.0, (double) privacyEligible / 25.0);
            String level = deriveDensityLevel(privacyEligible);
            List<List<Double>> boundary = GeometryUtils.toLeafletCoordinates(zone.getBoundary());

            points.add(new DensityHeatmapDto.ZoneDensityPointDto(
                zone.getId(),
                zone.getName(),
                zone.getCode(),
                zone.getCenterLat(),
                zone.getCenterLng(),
                boundary,
                privacyEligible,
                intensity,
                level
            ));
        }

        points.sort(Comparator.comparingLong(DensityHeatmapDto.ZoneDensityPointDto::userCount).reversed());

        return new DensityHeatmapDto(
            threshold,
            totalObserved,
            privacyEligibleTotal,
            suppressedCount,
            points
        );
    }

    private String deriveDensityLevel(long count) {
        if (count < 5) return "LOW";
        if (count < 15) return "MEDIUM";
        if (count < 30) return "HIGH";
        return "CRITICAL";
    }
}
