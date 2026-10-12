package com.nexusmap.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.locationtech.jts.geom.Point;

import java.time.Instant;

@Entity
@Table(
    name = "pins",
    indexes = {
        @Index(name = "idx_pins_status", columnList = "status"),
        @Index(name = "idx_pins_category", columnList = "category"),
        @Index(name = "idx_pins_expires_at", columnList = "expiresAt"),
        @Index(name = "idx_pins_creator", columnList = "creator_id")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Pin {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private PinCategory category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private PinStatus status = PinStatus.ACTIVE;

    @JsonIgnore
    @Column(nullable = false)
    private Point location;

    @Column(nullable = false)
    private double latitude;

    @Column(nullable = false)
    private double longitude;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "creator_id", nullable = false)
    private User creator;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;

    @Column(nullable = false)
    private Instant expiresAt;

    @OneToOne(mappedBy = "pin", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private Meetup meetup;

    public boolean isExpired() {
        return this.status == PinStatus.EXPIRED || Instant.now().isAfter(this.expiresAt);
    }

    public boolean isActive() {
        return this.status == PinStatus.ACTIVE && !isExpired();
    }
}
