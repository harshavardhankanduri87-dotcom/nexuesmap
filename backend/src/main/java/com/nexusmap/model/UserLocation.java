package com.nexusmap.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.locationtech.jts.geom.Point;

import java.time.Instant;

@Entity
@Table(
    name = "user_locations",
    indexes = {
        @Index(name = "idx_user_locations_user", columnList = "user_id"),
        @Index(name = "idx_user_locations_recorded", columnList = "recordedAt")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserLocation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @JsonIgnore
    @Column(nullable = false, columnDefinition = "geometry(Point, 4326)")
    private Point location;

    @Column(nullable = false)
    private double latitude;

    @Column(nullable = false)
    private double longitude;

    private Double accuracy;

    @Column(nullable = false, updatable = false)
    private Instant recordedAt;

    @PrePersist
    protected void onCreate() {
        if (this.recordedAt == null) {
            this.recordedAt = Instant.now();
        }
    }
}
