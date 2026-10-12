package com.nexusmap.repository;

import com.nexusmap.model.Zone;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ZoneRepository extends JpaRepository<Zone, Long> {

    List<Zone> findByActiveTrue();

    long countByActiveTrue();

    Optional<Zone> findByCode(String code);

    boolean existsByCode(String code);
}
