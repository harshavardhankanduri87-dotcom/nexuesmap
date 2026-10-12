package com.nexusmap.config;

import com.nexusmap.model.Zone;
import com.nexusmap.model.ZoneType;
import com.nexusmap.repository.ZoneRepository;
import com.nexusmap.util.GeometryUtils;
import lombok.RequiredArgsConstructor;
import org.locationtech.jts.geom.Polygon;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class CampusDataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(CampusDataInitializer.class);

    private final ZoneRepository zoneRepository;

    @Override
    public void run(String... args) {
        if (zoneRepository.count() > 0) {
            log.info("Campus zones already initialized (count: {}). Skipping seeding.", zoneRepository.count());
            return;
        }

        log.info("Initializing prototype campus geospatial zones...");

        // 1. Campus Perimeter Boundary
        createZoneIfNotExists(
            "Campus Perimeter Boundary",
            "ZONE-PERIMETER",
            "Overall designated perimeter of the prototype academic campus grounds.",
            ZoneType.CAMPUS_PERIMETER,
            37.7750,
            -122.4190,
            "#38bdf8",
            List.of(
                List.of(-122.4300, 37.7680),
                List.of(-122.4080, 37.7680),
                List.of(-122.4080, 37.7840),
                List.of(-122.4300, 37.7840)
            )
        );

        // 2. Main Gate (Transit)
        createZoneIfNotExists(
            "Main Gate & Welcome Transit Plaza",
            "ZONE-MAIN-GATE",
            "Primary campus entrance, transit hub, and shuttle pickup area.",
            ZoneType.TRANSIT,
            37.7710,
            -122.4245,
            "#22c55e",
            List.of(
                List.of(-122.4265, 37.7695),
                List.of(-122.4225, 37.7695),
                List.of(-122.4225, 37.7725),
                List.of(-122.4265, 37.7725)
            )
        );

        // 3. Central Academic Library (Academic)
        createZoneIfNotExists(
            "Central Academic Library",
            "ZONE-LIBRARY",
            "University archive collections, quiet study rooms, and research commons.",
            ZoneType.ACADEMIC,
            37.7745,
            -122.4200,
            "#eab308",
            List.of(
                List.of(-122.4220, 37.7730),
                List.of(-122.4180, 37.7730),
                List.of(-122.4180, 37.7760),
                List.of(-122.4220, 37.7760)
            )
        );

        // 4. Student Canteen & Dining (Dining)
        createZoneIfNotExists(
            "Student Canteen & Dining Commons",
            "ZONE-CANTEEN",
            "Food hall, dining commons, and student social terrace.",
            ZoneType.DINING,
            37.7785,
            -122.4200,
            "#f97316",
            List.of(
                List.of(-122.4220, 37.7770),
                List.of(-122.4180, 37.7770),
                List.of(-122.4180, 37.7800),
                List.of(-122.4220, 37.7800)
            )
        );

        // 5. Innovation Lab & Maker Studio (Academic)
        createZoneIfNotExists(
            "Innovation Lab & Tech Maker Studio",
            "ZONE-INNOVATION-LAB",
            "Advanced hardware prototyping lab, AI incubation lab, and collaborative space.",
            ZoneType.ACADEMIC,
            37.7775,
            -122.4145,
            "#00f0ff",
            List.of(
                List.of(-122.4165, 37.7760),
                List.of(-122.4125, 37.7760),
                List.of(-122.4125, 37.7790),
                List.of(-122.4165, 37.7790)
            )
        );

        // 6. Grand University Auditorium (Recreational)
        createZoneIfNotExists(
            "Grand University Auditorium",
            "ZONE-AUDITORIUM",
            "Keynote conference center, cultural theater, and hackathon hall.",
            ZoneType.RECREATIONAL,
            37.7785,
            -122.4250,
            "#a855f7",
            List.of(
                List.of(-122.4270, 37.7770),
                List.of(-122.4230, 37.7770),
                List.of(-122.4230, 37.7800),
                List.of(-122.4270, 37.7800)
            )
        );

        log.info("Successfully provisioned {} campus zones.", zoneRepository.count());
    }

    private void createZoneIfNotExists(
            String name,
            String code,
            String description,
            ZoneType type,
            double centerLat,
            double centerLng,
            String color,
            List<List<Double>> coordPairs
    ) {
        if (zoneRepository.existsByCode(code)) return;

        Polygon boundary = GeometryUtils.createPolygon(coordPairs);
        Zone zone = Zone.builder()
            .name(name)
            .code(code)
            .description(description)
            .zoneType(type)
            .active(true)
            .centerLat(centerLat)
            .centerLng(centerLng)
            .color(color)
            .boundary(boundary)
            .build();

        zoneRepository.save(zone);
    }
}
