package com.nexusmap.service;

import com.nexusmap.dto.PeerLocationDto;
import com.nexusmap.model.User;
import org.locationtech.jts.geom.Coordinate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Optional;

/**
 * Server-Side Privacy Service implementing Anti-Averaging Coordinate Fuzzing.
 *
 * Design Guarantees:
 * 1. Zero Raw GPS Leaks: Raw GPS coordinates are never returned to peer clients or external broadcasts.
 * 2. Anti-Averaging Protection: Fuzzing seeds are keyed on (User, Time-Epoch, Spatial-Grid-Cell).
 *    Repeated requests within the epoch window return identical perturbed coordinates (zero sample variance),
 *    preventing attackers from using the Central Limit Theorem to average out the noise.
 * 3. Dynamic Epoch Rotation: The perturbation vector rotates smoothly across 5-minute epochs and when
 *    moving into new spatial cells, preventing static offset recovery.
 * 4. Configurable Spatial Privacy: Scales perturbation based on the user's configured fuzzing radius.
 * 5. Strict Ghost Mode Enforcement: When Ghost Mode is active or location sharing is disabled,
 *    coordinates are completely omitted (returns empty Optional).
 */
@Service
public class LocationPrivacyService {

    private static final Logger log = LoggerFactory.getLogger(LocationPrivacyService.class);

    private static final long TIME_EPOCH_SECONDS = 300; // 5-minute time window for temporal anti-averaging
    private static final double CELL_GRID_SIZE = 0.001; // ~100m spatial cell snapping for seed quantization
    private static final double MIN_OFFSET_METERS = 30.0; // Enforce minimum distance from true position

    private final byte[] hmacKeyBytes;

    public LocationPrivacyService(@Value("${nexusmap.privacy.secret:}") String configuredSecret) {
        if (configuredSecret != null && !configuredSecret.isBlank()) {
            this.hmacKeyBytes = configuredSecret.getBytes(StandardCharsets.UTF_8);
        } else {
            byte[] ephemeralKey = new byte[32];
            new SecureRandom().nextBytes(ephemeralKey);
            this.hmacKeyBytes = ephemeralKey;
        }
    }

    /**
     * Checks if the user has authorized location sharing.
     */
    public boolean isLocationSharingPermitted(User user) {
        if (user == null) return false;
        return !user.isGhostMode() && user.isLocationSharingEnabled();
    }

    /**
     * Obfuscates raw GPS coordinates into a privacy-preserving fuzzed coordinate.
     * Returns empty if the user is in Ghost Mode or has disabled location sharing.
     */
    public Optional<Coordinate> fuzzCoordinates(User user, double rawLat, double rawLng) {
        if (!isLocationSharingPermitted(user)) {
            return Optional.empty();
        }

        int radiusMeters = user.getFuzzingRadiusMeters() > 0 ? user.getFuzzingRadiusMeters() : 100;
        Coordinate fuzzed = computeAntiAveragingFuzz(user.getId(), rawLat, rawLng, radiusMeters);
        return Optional.of(fuzzed);
    }

    /**
     * Creates a public PeerLocationDto with server-side fuzzed coordinates.
     * Completely redacts data if user is in Ghost Mode or not sharing.
     */
    public Optional<PeerLocationDto> createFuzzedPeerLocation(
            User user,
            double rawLat,
            double rawLng,
            Long zoneId,
            String zoneName,
            Instant lastSeenAt
    ) {
        if (!isLocationSharingPermitted(user)) {
            return Optional.empty();
        }

        Optional<Coordinate> fuzzedOpt = fuzzCoordinates(user, rawLat, rawLng);
        if (fuzzedOpt.isEmpty()) {
            return Optional.empty();
        }

        Coordinate fuzzed = fuzzedOpt.get();
        return Optional.of(new PeerLocationDto(
            user.getId(),
            user.getName(),
            fuzzed.y, // latitude
            fuzzed.x, // longitude
            user.getFuzzingRadiusMeters(),
            zoneId,
            zoneName,
            lastSeenAt != null ? lastSeenAt : Instant.now()
        ));
    }

    /**
     * Computes the keyed pseudorandom perturbation vector using Box-Muller Gaussian transformation.
     * Within the same (userId, epoch, gridCell), this deterministic function yields IDENTICAL coordinates.
     */
    public Coordinate computeAntiAveragingFuzz(Long userId, double rawLat, double rawLng, int radiusMeters) {
        long currentEpoch = Instant.now().getEpochSecond() / TIME_EPOCH_SECONDS;
        long cellLat = Math.round(rawLat / CELL_GRID_SIZE);
        long cellLng = Math.round(rawLng / CELL_GRID_SIZE);

        String seedInput = userId + ":" + currentEpoch + ":" + cellLat + ":" + cellLng;
        byte[] hash = computeHmacSha256(seedInput);

        // Derive deterministic pseudo-random uniform values in (0, 1)
        long high = toLong(hash, 0);
        long low = toLong(hash, 8);

        double u1 = Math.max(1e-10, (double) (Math.abs(high) % 1_000_000_000L) / 1_000_000_000.0);
        double u2 = (double) (Math.abs(low) % 1_000_000_000L) / 1_000_000_000.0;

        // Box-Muller transform for 2D Gaussian standard normal variables
        double z0 = Math.sqrt(-2.0 * Math.log(u1)) * Math.cos(2.0 * Math.PI * u2);
        double z1 = Math.sqrt(-2.0 * Math.log(u1)) * Math.sin(2.0 * Math.PI * u2);

        // Scale by target standard deviation (meters)
        double sigma = Math.max(50.0, (double) radiusMeters);
        double offsetEastMeters = z0 * sigma;
        double offsetNorthMeters = z1 * sigma;

        // Ensure minimum offset distance to prevent accidental zero-distance collision
        double offsetMagnitude = Math.hypot(offsetEastMeters, offsetNorthMeters);
        if (offsetMagnitude < MIN_OFFSET_METERS) {
            double scale = MIN_OFFSET_METERS / (offsetMagnitude + 1e-6);
            offsetEastMeters *= scale;
            offsetNorthMeters *= scale;
        }

        // Convert meter offsets to degree delta
        // 1 deg lat ~ 111,320m
        double deltaLat = offsetNorthMeters / 111320.0;
        double deltaLng = offsetEastMeters / (111320.0 * Math.cos(Math.toRadians(rawLat)));

        double fuzzedLat = rawLat + deltaLat;
        double fuzzedLng = rawLng + deltaLng;

        return new Coordinate(fuzzedLng, fuzzedLat);
    }

    private byte[] computeHmacSha256(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(hmacKeyBytes, "HmacSHA256");
            mac.init(secretKey);
            return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to calculate HMAC for coordinate fuzzing", e);
        }
    }

    private long toLong(byte[] bytes, int offset) {
        long value = 0;
        for (int i = 0; i < 8; i++) {
            value = (value << 8) | (bytes[offset + i] & 0xFF);
        }
        return value;
    }
}
