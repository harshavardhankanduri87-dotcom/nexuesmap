package com.nexusmap.dto;

import com.nexusmap.model.Meetup;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MeetupResponseDto {

    private Long id;
    private Long pinId;
    private Instant startTime;
    private Instant endTime;
    private Integer capacity;
    private int currentParticipants;
    @com.fasterxml.jackson.annotation.JsonProperty("isJoined")
    private boolean isJoined;
    @com.fasterxml.jackson.annotation.JsonProperty("isFull")
    private boolean isFull;
    private boolean active;

    public static MeetupResponseDto fromEntity(Meetup meetup, Long currentUserId) {
        if (meetup == null) return null;

        int participantCount = meetup.getParticipantCount();
        boolean isUserJoined = false;
        if (currentUserId != null && meetup.getParticipants() != null) {
            isUserJoined = meetup.getParticipants().stream()
                .anyMatch(p -> p.getUser().getId().equals(currentUserId));
        }

        return MeetupResponseDto.builder()
            .id(meetup.getId())
            .pinId(meetup.getPin() != null ? meetup.getPin().getId() : null)
            .startTime(meetup.getStartTime())
            .endTime(meetup.getEndTime())
            .capacity(meetup.getCapacity())
            .currentParticipants(participantCount)
            .isJoined(isUserJoined)
            .isFull(meetup.isFull())
            .active(meetup.isActive())
            .build();
    }
}
