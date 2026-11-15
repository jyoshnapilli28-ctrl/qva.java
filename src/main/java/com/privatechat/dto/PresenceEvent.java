package com.privatechat.dto;

/**
 * WebSocket presence event (user connected / disconnected).
 */
public class PresenceEvent {

    public enum EventType { CONNECTED, DISCONNECTED }

    private EventType type;
    private String username;
    private String displayName;

    public PresenceEvent() {}

    public PresenceEvent(EventType type, String username, String displayName) {
        this.type = type;
        this.username = username;
        this.displayName = displayName;
    }

    public EventType getType() { return type; }
    public void setType(EventType type) { this.type = type; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
}

