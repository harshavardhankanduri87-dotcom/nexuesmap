package com.nexusmap.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PinEventDto {

    private String eventType; // PIN_CREATED, PIN_UPDATED, PIN_DELETED, MEETUP_JOINED, MEETUP_LEFT
    private Long pinId;
    private PinResponseDto pin;
    @Builder.Default
    private Instant timestamp = Instant.now();
}
