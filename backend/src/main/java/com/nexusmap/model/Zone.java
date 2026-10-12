package com.nexusmap.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.locationtech.jts.geom.Polygon;

import java.time.Instant;

@Entity
@Table(
    name = "zones",
    indexes = {
        @Index(name = "idx_zones_code", columnList = "code", unique = true),
        @Index(name = "idx_zones_active", columnList = "active")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Zone {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, unique = true, length = 50)
    private String code;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private ZoneType zoneType = ZoneType.ACADEMIC;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    @JsonIgnore
    @Column(nullable = false, columnDefinition = "geometry(Polygon, 4326)")
    private Polygon boundary;

    @Column(nullable = false)
    private Double centerLat;

    @Column(nullable = false)
    private Double centerLng;

    @Column(length = 20)
    @Builder.Default
    private String color = "#00f0ff";

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;
}
