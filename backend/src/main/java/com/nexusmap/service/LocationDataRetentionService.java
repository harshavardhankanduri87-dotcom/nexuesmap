package com.nexusmap.service;

import com.nexusmap.repository.UserLocationRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

/**
 * Enforces Ephemeral Location Data Retention Policy.
 *
 * Requirements:
 * - Transient GPS telemetry is purged every 5 minutes.
 * - Records older than retention cutoff (default: 30 minutes) are permanently deleted.
 * - Prevents accumulation of historical location trails.
 */
@Service
@RequiredArgsConstructor
public class LocationDataRetentionService {

    private static final Logger log = LoggerFactory.getLogger(LocationDataRetentionService.class);

    private final UserLocationRepository userLocationRepository;

    @Value("${nexusmap.privacy.retention-minutes:30}")
    private int retentionMinutes = 30;

    /**
     * Scheduled cleanup job running every 5 minutes.
     */
    @Scheduled(fixedDelayString = "${nexusmap.privacy.retention-cleanup-ms:300000}")
    @Transactional
    public void executeScheduledPruning() {
        int purged = purgeExpiredLocations(Duration.ofMinutes(retentionMinutes));
        if (purged > 0) {
            log.info("Ephemeral location retention policy executed: purged {} stale location records older than {} minutes",
                purged, retentionMinutes);
        }
    }

    /**
     * Purges location records recorded before the calculated cutoff.
     */
    @Transactional
    public int purgeExpiredLocations(Duration maxAge) {
        Instant cutoff = Instant.now().minus(maxAge);
        return userLocationRepository.deleteByRecordedAtBefore(cutoff);
    }
}
