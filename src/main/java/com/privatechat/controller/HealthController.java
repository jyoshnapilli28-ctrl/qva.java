package com.privatechat.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

/**
 * Application health and diagnostic endpoints.
 */
@RestController
@RequestMapping("/api/health")
public class HealthController {

    private final long startTime = System.currentTimeMillis();

    @GetMapping
    public ResponseEntity<Map<String, Object>> health() {
        long uptimeMs = System.currentTimeMillis() - startTime;
        return ResponseEntity.ok(Map.of(
            "status", "UP",
            "service", "private-chat",
            "version", "1.0.0",
            "timestamp", Instant.now().toString(),
            "uptimeSeconds", uptimeMs / 1000
        ));
    }
}

