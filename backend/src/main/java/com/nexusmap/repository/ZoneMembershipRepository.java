package com.nexusmap.repository;

import com.nexusmap.model.User;
import com.nexusmap.model.Zone;
import com.nexusmap.model.ZoneMembership;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ZoneMembershipRepository extends JpaRepository<ZoneMembership, Long> {

    List<ZoneMembership> findAllByUserAndActiveTrue(User user);

    default Optional<ZoneMembership> findByUserAndActiveTrue(User user) {
        List<ZoneMembership> list = findAllByUserAndActiveTrue(user);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    List<ZoneMembership> findAllByUserAndZoneIdAndActiveTrue(User user, Long zoneId);

    default Optional<ZoneMembership> findByUserAndZoneIdAndActiveTrue(User user, Long zoneId) {
        List<ZoneMembership> list = findAllByUserAndZoneIdAndActiveTrue(user, zoneId);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    List<ZoneMembership> findAllByZoneAndActiveTrue(Zone zone);

    long countByZoneAndActiveTrue(Zone zone);

    @org.springframework.data.jpa.repository.Query("""
        SELECT count(zm) FROM ZoneMembership zm
        JOIN zm.user u
        WHERE zm.zone = :zone
          AND zm.active = true
          AND u.ghostMode = false
          AND u.locationSharingEnabled = true
    """)
    long countPrivacyEligibleMembersByZone(@org.springframework.data.repository.query.Param("zone") Zone zone);

    List<ZoneMembership> findAllByUserOrderByEnteredAtDesc(User user);
}
