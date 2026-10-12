package com.nexusmap.repository;

import com.nexusmap.model.User;
import com.nexusmap.model.UserLocation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserLocationRepository extends JpaRepository<UserLocation, Long> {

    Optional<UserLocation> findFirstByUserOrderByRecordedAtDesc(User user);

    List<UserLocation> findTop10ByUserOrderByRecordedAtDesc(User user);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("DELETE FROM UserLocation ul WHERE ul.recordedAt < :threshold")
    int deleteByRecordedAtBefore(java.time.Instant threshold);

    @org.springframework.data.jpa.repository.Query("SELECT COUNT(DISTINCT ul.user.id) FROM UserLocation ul WHERE ul.recordedAt >= :cutoff")
    long countUniqueActiveUsersSince(@org.springframework.data.repository.query.Param("cutoff") java.time.Instant cutoff);

    long countByRecordedAtBetween(java.time.Instant start, java.time.Instant end);
}
