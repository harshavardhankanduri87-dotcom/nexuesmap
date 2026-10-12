package com.nexusmap.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SendChatMessageRequest(
    @NotNull(message = "Target zone ID is required")
    Long zoneId,

    @NotBlank(message = "Message content cannot be blank")
    @Size(max = 1000, message = "Message content cannot exceed 1000 characters")
    String content
) {
}
