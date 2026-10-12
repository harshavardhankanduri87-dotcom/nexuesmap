package com.nexusmap.dto;

import com.nexusmap.model.PinCategory;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdatePinRequest {

    @Size(min = 3, max = 100, message = "Pin title must be between 3 and 100 characters")
    private String title;

    @Size(max = 2000, message = "Pin description cannot exceed 2,000 characters")
    private String description;

    private PinCategory category;

    private Instant expiresAt;

    @Valid
    private CreateMeetupDetailsDto meetupDetails;
}
