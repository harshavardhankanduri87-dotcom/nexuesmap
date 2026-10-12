package com.nexusmap.dto;

import com.nexusmap.model.Role;
import com.nexusmap.model.User;

import java.time.Instant;

public record UserSummaryDto(
    Long id,
    String name,
    String email,
    Role role,
    boolean ghostMode,
    Instant createdAt
) {
    public static UserSummaryDto fromEntity(User user) {
        return new UserSummaryDto(
            user.getId(),
            user.getName(),
            user.getEmail(),
            user.getRole(),
            user.isGhostMode(),
            user.getCreatedAt()
        );
    }
}
