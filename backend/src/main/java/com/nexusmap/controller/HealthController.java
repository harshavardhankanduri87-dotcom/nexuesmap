package com.nexusmap.controller;

import com.nexusmap.dto.HealthResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/health")
public class HealthController {

    @Value("${nexusmap.app-name:NEXUSMAP}")
    private String appName;

    @Value("${nexusmap.version:1.0.0}")
    private String version;

    @Value("${spring.profiles.active:dev}")
    private String activeProfile;

    @GetMapping
    public ResponseEntity<HealthResponse> checkHealth() {
        Map<String, String> subsystems = new LinkedHashMap<>();
        subsystems.put("geospatialEngine", "READY");
        subsystems.put("securityFilter", "ACTIVE");
        subsystems.put("webSocketBroker", "STANDBY");
        subsystems.put("cacheLayer", "INITIALIZED");

        HealthResponse response = new HealthResponse(
            "UP",
            appName,
            version,
            Instant.now(),
            activeProfile,
            subsystems
        );

        return ResponseEntity.ok(response);
    }

    @GetMapping("/ping")
    public ResponseEntity<Map<String, Object>> ping() {
        return ResponseEntity.ok(Map.of(
            "message", "pong",
            "timestamp", Instant.now().toString()
        ));
    }
}
