package com.nexusmap.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record UpdatePrivacySettingsRequest(
    Boolean ghostMode,
    Boolean locationSharingEnabled,
    @Min(value = 50, message = "Fuzzing radius must be at least 50 meters")
    @Max(value = 500, message = "Fuzzing radius cannot exceed 500 meters")
    Integer fuzzingRadiusMeters
) {}
