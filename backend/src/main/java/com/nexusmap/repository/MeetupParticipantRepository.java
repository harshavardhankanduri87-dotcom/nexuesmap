package com.nexusmap.repository;

import com.nexusmap.model.MeetupParticipant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface MeetupParticipantRepository extends JpaRepository<MeetupParticipant, Long> {

    boolean existsByMeetupIdAndUserId(Long meetupId, Long userId);

    Optional<MeetupParticipant> findByMeetupIdAndUserId(Long meetupId, Long userId);

    long countByMeetupId(Long meetupId);

    void deleteByMeetupIdAndUserId(Long meetupId, Long userId);
}
