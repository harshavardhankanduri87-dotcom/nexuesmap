package com.nexusmap.dto;

import com.nexusmap.model.PinCategory;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreatePinRequest {

    @NotBlank(message = "Pin title is required")
    @Size(min = 3, max = 100, message = "Pin title must be between 3 and 100 characters")
    private String title;

    @NotBlank(message = "Pin description is required")
    @Size(max = 2000, message = "Pin description cannot exceed 2,000 characters")
    private String description;

    @NotNull(message = "Pin category is required")
    private PinCategory category;

    @NotNull(message = "Latitude is required")
    @DecimalMin(value = "-90.0", message = "Latitude must be >= -90.0")
    @DecimalMax(value = "90.0", message = "Latitude must be <= 90.0")
    private Double latitude;

    @NotNull(message = "Longitude is required")
    @DecimalMin(value = "-180.0", message = "Longitude must be >= -180.0")
    @DecimalMax(value = "180.0", message = "Longitude must be <= 180.0")
    private Double longitude;

    @Min(value = 1, message = "Duration must be at least 1 hour")
    @Max(value = 720, message = "Duration cannot exceed 720 hours (30 days)")
    @Builder.Default
    private Integer durationHours = 24;

    private Instant expiresAt;

    @Valid
    private CreateMeetupDetailsDto meetupDetails;
}
