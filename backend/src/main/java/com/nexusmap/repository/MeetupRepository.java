package com.nexusmap.repository;

import com.nexusmap.model.Meetup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

@Repository
public interface MeetupRepository extends JpaRepository<Meetup, Long> {

    Optional<Meetup> findByPinId(Long pinId);

    @Query("""
        SELECT m FROM Meetup m
        LEFT JOIN FETCH m.pin p
        LEFT JOIN FETCH m.participants parts
        WHERE m.id = :id
    """)
    Optional<Meetup> findDetailedById(@Param("id") Long id);

    @Query("""
        SELECT m FROM Meetup m
        LEFT JOIN FETCH m.pin p
        LEFT JOIN FETCH m.participants parts
        WHERE m.pin.id = :pinId
    """)
    Optional<Meetup> findDetailedByPinId(@Param("pinId") Long pinId);

    @Query("""
        SELECT count(m) FROM Meetup m
        JOIN m.pin p
        WHERE m.active = true
          AND p.status = 'ACTIVE'
          AND p.expiresAt > :now
          AND (m.endTime IS NULL OR m.endTime > :now)
    """)
    long countActiveMeetups(@Param("now") Instant now);
}
