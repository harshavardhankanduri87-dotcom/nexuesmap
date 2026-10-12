package com.nexusmap.repository;

import com.nexusmap.model.Pin;
import com.nexusmap.model.PinCategory;
import com.nexusmap.model.PinStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface PinRepository extends JpaRepository<Pin, Long> {

    Optional<Pin> findByIdAndStatusNot(Long id, PinStatus status);

    @Query("""
        SELECT p FROM Pin p
        LEFT JOIN FETCH p.creator c
        LEFT JOIN FETCH p.meetup m
        WHERE p.id = :id
          AND p.status <> :excludedStatus
    """)
    Optional<Pin> findDetailedByIdAndStatusNot(@Param("id") Long id, @Param("excludedStatus") PinStatus excludedStatus);

    @Query("""
        SELECT p FROM Pin p
        LEFT JOIN FETCH p.creator c
        LEFT JOIN FETCH p.meetup m
        WHERE p.status = 'ACTIVE'
          AND p.expiresAt > :now
          AND p.latitude BETWEEN :minLat AND :maxLat
          AND p.longitude BETWEEN :minLng AND :maxLng
          AND (:category IS NULL OR p.category = :category)
    """)
    List<Pin> findActivePinsInBoundingBox(
        @Param("minLat") double minLat,
        @Param("maxLat") double maxLat,
        @Param("minLng") double minLng,
        @Param("maxLng") double maxLng,
        @Param("category") PinCategory category,
        @Param("now") Instant now
    );

    @Query("""
        SELECT p FROM Pin p
        WHERE p.status = 'ACTIVE'
          AND p.expiresAt <= :now
    """)
    List<Pin> findExpiredPinsToUpdate(@Param("now") Instant now);

    long countByCreatorIdAndCreatedAtAfter(Long creatorId, Instant after);

    long countByStatusAndExpiresAtAfter(PinStatus status, Instant now);

    long countByCreatedAtBetween(Instant start, Instant end);
}
