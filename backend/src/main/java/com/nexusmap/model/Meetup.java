package com.nexusmap.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(
    name = "meetups",
    indexes = {
        @Index(name = "idx_meetups_pin", columnList = "pin_id", unique = true),
        @Index(name = "idx_meetups_start", columnList = "startTime"),
        @Index(name = "idx_meetups_active", columnList = "active")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Meetup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pin_id", nullable = false, unique = true)
    private Pin pin;

    @Column(nullable = false)
    private Instant startTime;

    private Instant endTime;

    private Integer capacity;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    @OneToMany(mappedBy = "meetup", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private Set<MeetupParticipant> participants = new HashSet<>();

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    public boolean isFull() {
        if (capacity == null || capacity <= 0) {
            return false;
        }
        return participants != null && participants.size() >= capacity;
    }

    public int getParticipantCount() {
        return participants == null ? 0 : participants.size();
    }
}
