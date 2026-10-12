package com.nexusmap.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(
    name = "meetup_participants",
    uniqueConstraints = {
        @UniqueConstraint(name = "uq_meetup_participant", columnNames = {"meetup_id", "user_id"})
    },
    indexes = {
        @Index(name = "idx_meetup_participants_meetup", columnList = "meetup_id"),
        @Index(name = "idx_meetup_participants_user", columnList = "user_id")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MeetupParticipant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "meetup_id", nullable = false)
    private Meetup meetup;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant joinedAt;
}
