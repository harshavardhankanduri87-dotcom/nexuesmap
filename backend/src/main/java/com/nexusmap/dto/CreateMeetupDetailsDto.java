package com.nexusmap.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateMeetupDetailsDto {

    @NotNull(message = "Meetup start time is required")
    private Instant startTime;

    private Instant endTime;

    @Min(value = 1, message = "Participant capacity must be at least 1")
    @Max(value = 10000, message = "Participant capacity cannot exceed 10,000")
    private Integer capacity;
}
