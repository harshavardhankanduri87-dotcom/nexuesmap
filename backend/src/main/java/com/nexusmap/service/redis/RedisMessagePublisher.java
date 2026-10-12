package com.nexusmap.service.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexusmap.dto.ChatMessageDto;
import com.nexusmap.dto.PresenceEventDto;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RedisMessagePublisher {

    private static final Logger log = LoggerFactory.getLogger(RedisMessagePublisher.class);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public static final String CHAT_CHANNEL_PREFIX = "nexusmap:zone:";
    public static final String CHAT_CHANNEL_SUFFIX = ":messages";
    public static final String PRESENCE_CHANNEL_SUFFIX = ":presence";
    public static final String PINS_CHANNEL = "nexusmap:pins:events";

    /**
     * Publishes a pin or meetup lifecycle event to Redis Pub/Sub.
     */
    public boolean publishPinEvent(Object pinEventDto) {
        try {
            String json = objectMapper.writeValueAsString(pinEventDto);
            redisTemplate.convertAndSend(PINS_CHANNEL, json);
            return true;
        } catch (Exception ex) {
            log.warn("Failed to publish pin event to Redis channel '{}': {}. Falling back to local broker.", PINS_CHANNEL, ex.getMessage());
            return false;
        }
    }

    /**
     * Publishes a chat message to Redis Pub/Sub for cross-instance fanout.
     * Fails gracefully if Redis is unavailable.
     */
    public boolean publishChatMessage(Long zoneId, ChatMessageDto messageDto) {
        String channel = CHAT_CHANNEL_PREFIX + zoneId + CHAT_CHANNEL_SUFFIX;
        try {
            String json = objectMapper.writeValueAsString(messageDto);
            redisTemplate.convertAndSend(channel, json);
            return true;
        } catch (Exception ex) {
            log.warn("Failed to publish chat message to Redis channel '{}': {}. Falling back to local broker.", channel, ex.getMessage());
            return false;
        }
    }

    /**
     * Publishes a presence event to Redis Pub/Sub.
     */
    public boolean publishPresenceEvent(Long zoneId, PresenceEventDto presenceDto) {
        String channel = CHAT_CHANNEL_PREFIX + zoneId + PRESENCE_CHANNEL_SUFFIX;
        try {
            String json = objectMapper.writeValueAsString(presenceDto);
            redisTemplate.convertAndSend(channel, json);
            return true;
        } catch (Exception ex) {
            log.warn("Failed to publish presence event to Redis channel '{}': {}. Falling back to local broker.", channel, ex.getMessage());
            return false;
        }
    }
}
