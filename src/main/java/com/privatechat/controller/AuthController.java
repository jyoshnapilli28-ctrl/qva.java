package com.privatechat.controller;

import com.privatechat.dto.UserDto;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.Map;

/**
 * Authentication endpoints consumed by the browser frontend.
 * Login/logout are handled by Spring Security form-login mechanism.
 * These endpoints provide supporting information.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    /**
     * Returns the currently authenticated user's information.
     * Called by the frontend after login to populate the UI.
     */
    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> getCurrentUser(Principal principal) {
        if (principal == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));
        }
        return ResponseEntity.ok(Map.of(
            "username", principal.getName(),
            "authenticated", true
        ));
    }

    /**
     * Simple status check; also used by client launcher to verify connectivity.
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status() {
        return ResponseEntity.ok(Map.of("status", "ok", "service", "private-chat"));
    }
}

