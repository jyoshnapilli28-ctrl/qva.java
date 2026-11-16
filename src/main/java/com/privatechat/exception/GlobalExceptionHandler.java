package com.privatechat.exception;

import com.privatechat.operations.OperationsException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Centralized exception handler for REST controllers.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ChatException.class)
    public ResponseEntity<Map<String, Object>> handleChatException(ChatException ex) {
        log.warn("Chat error: {}", ex.getMessage());
        return error(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Map<String, Object>> handleAuthException(AuthenticationException ex) {
        log.warn("Authentication error: {}", ex.getMessage());
        return error(HttpStatus.UNAUTHORIZED, "Authentication required");
    }

    @ExceptionHandler(OperationsException.class)
    public ResponseEntity<Map<String, Object>> handleOperationsException(OperationsException ex) {
        return error(ex.status(), ex.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneralException(Exception ex) {
        if (ex instanceof NoResourceFoundException missing
                && (missing.getResourcePath().equals("app/index.html")
                    || missing.getResourcePath().startsWith("app/assets/"))) {
            return error(HttpStatus.NOT_FOUND, "QVA web asset not found");
        }
        log.error("Unexpected error", ex);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");
    }

    private ResponseEntity<Map<String, Object>> error(HttpStatus status, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("message", message);
        return ResponseEntity.status(status).body(body);
    }
}

