package com.nexusmap.dto;

import com.nexusmap.model.Pin;
import com.nexusmap.model.PinCategory;
import com.nexusmap.model.PinStatus;
import com.nexusmap.model.User;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PinResponseDto {

    private Long id;
    private String title;
    private String description;
    private PinCategory category;
    private PinStatus status;
    private double latitude;
    private double longitude;
    private Double distanceMeters;
    private Long creatorId;
    private String creatorName;
    @com.fasterxml.jackson.annotation.JsonProperty("isCreator")
    private boolean isCreator;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant expiresAt;
    private MeetupResponseDto meetup;

    public static PinResponseDto fromEntity(Pin pin, User currentUser, Double distanceMeters) {
        if (pin == null) return null;

        User creator = pin.getCreator();
        Long creatorId = null;
        String creatorName = "Anonymous";
        boolean isCurrentUserCreator = false;

        if (currentUser != null && creator != null && creator.getId().equals(currentUser.getId())) {
            isCurrentUserCreator = true;
        }

        if (creator != null) {
            // Mask identity if creator is in Ghost Mode, unless current user is the creator
            if (creator.isGhostMode() && !isCurrentUserCreator) {
                creatorId = null;
                creatorName = "Ghost Operator";
            } else {
                creatorId = creator.getId();
                creatorName = creator.getName();
            }
        }

        MeetupResponseDto meetupDto = null;
        if (pin.getMeetup() != null) {
            Long currentUserId = currentUser != null ? currentUser.getId() : null;
            meetupDto = MeetupResponseDto.fromEntity(pin.getMeetup(), currentUserId);
        }

        return PinResponseDto.builder()
            .id(pin.getId())
            .title(pin.getTitle())
            .description(pin.getDescription())
            .category(pin.getCategory())
            .status(pin.getStatus())
            .latitude(pin.getLatitude())
            .longitude(pin.getLongitude())
            .distanceMeters(distanceMeters)
            .creatorId(creatorId)
            .creatorName(creatorName)
            .isCreator(isCurrentUserCreator)
            .createdAt(pin.getCreatedAt())
            .updatedAt(pin.getUpdatedAt())
            .expiresAt(pin.getExpiresAt())
            .meetup(meetupDto)
            .build();
    }
}
