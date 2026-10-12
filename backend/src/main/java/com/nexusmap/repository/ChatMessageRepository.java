package com.nexusmap.repository;

import com.nexusmap.model.ChatMessage;
import com.nexusmap.model.Zone;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    Page<ChatMessage> findByZoneOrderByCreatedAtDesc(Zone zone, Pageable pageable);

    List<ChatMessage> findTop50ByZoneOrderByCreatedAtDesc(Zone zone);

    long countByCreatedAtAfter(java.time.Instant after);

    long countByCreatedAtBetween(java.time.Instant start, java.time.Instant end);
}
