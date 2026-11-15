package com.privatechat.controller;

import com.privatechat.dto.MessageDto;
import com.privatechat.service.MessageService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.Map;

/**
 * REST endpoints for message history and sending via HTTP (fallback).
 */
@RestController
@RequestMapping("/api/rooms")
public class MessageController {

    private final MessageService messageService;

    public MessageController(MessageService messageService) {
        this.messageService = messageService;
    }

    /**
     * Retrieves message history for a chat room.
     * Called on page load to display existing messages.
     */
    @GetMapping("/{roomName}/messages")
    public ResponseEntity<List<MessageDto>> getHistory(
            @PathVariable String roomName,
            @RequestParam(defaultValue = "100") int limit) {
        return ResponseEntity.ok(messageService.getHistory(roomName, limit));
    }

    /**
     * Room information.
     */
    @GetMapping("/{roomName}")
    public ResponseEntity<Map<String, Object>> getRoomInfo(@PathVariable String roomName) {
        return ResponseEntity.ok(Map.of(
            "name", roomName,
            "displayName", roomName.substring(0, 1).toUpperCase() + roomName.substring(1),
            "topic", "/topic/messages/" + roomName
        ));
    }
}

