package com.nexusmap.dto;

import java.util.List;

public record PaginatedMessagesResponse(
    List<ChatMessageDto> messages,
    int page,
    int size,
    int totalPages,
    long totalElements
) {
}
