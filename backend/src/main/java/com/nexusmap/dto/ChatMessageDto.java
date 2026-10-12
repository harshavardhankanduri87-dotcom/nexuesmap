package com.nexusmap.dto;

import com.nexusmap.model.ChatMessage;

import java.time.Instant;

public record ChatMessageDto(
    Long id,
    Long senderId,
    String senderName,
    Long zoneId,
    String zoneCode,
    String content,
    Instant timestamp,
    boolean ghostSender
) {
    public static ChatMessageDto fromEntity(ChatMessage message) {
        boolean isGhost = message.getSender().isGhostMode();
        String name = isGhost ? "Ghost Operator #" + (message.getSender().getId() % 1000) : message.getSender().getName();
        Long sId = isGhost ? null : message.getSender().getId();

        return new ChatMessageDto(
            message.getId(),
            sId,
            name,
            message.getZone().getId(),
            message.getZone().getCode(),
            message.getContent(),
            message.getCreatedAt(),
            isGhost
        );
    }
}
