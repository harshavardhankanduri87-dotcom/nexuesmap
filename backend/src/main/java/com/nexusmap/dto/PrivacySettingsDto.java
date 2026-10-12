package com.nexusmap.dto;

import com.nexusmap.model.User;

import java.time.Instant;

public record PrivacySettingsDto(
    boolean ghostMode,
    boolean locationSharingEnabled,
    int fuzzingRadiusMeters,
    Instant updatedAt
) {
    public static PrivacySettingsDto fromUser(User user) {
        return new PrivacySettingsDto(
            user.isGhostMode(),
            user.isLocationSharingEnabled(),
            user.getFuzzingRadiusMeters(),
            user.getUpdatedAt() != null ? user.getUpdatedAt() : Instant.now()
        );
    }
}
