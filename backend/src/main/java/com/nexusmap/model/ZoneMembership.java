package com.nexusmap.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Entity
@Table(
    name = "zone_memberships",
    indexes = {
        @Index(name = "idx_zone_memberships_user", columnList = "user_id"),
        @Index(name = "idx_zone_memberships_zone", columnList = "zone_id"),
        @Index(name = "idx_zone_memberships_active", columnList = "active")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ZoneMembership {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "zone_id", nullable = false)
    private Zone zone;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant enteredAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant lastSeenAt;

    private Instant exitedAt;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;
}
