package com.privatechat.dto;

import java.time.Instant;

/**
 * Data transfer object for messages sent over REST and WebSocket.
 */
public class MessageDto {

    private Long id;
    private String username;
    private String displayName;
    private String content;
    private Instant sentAt;
    private String roomName;

    public MessageDto() {}

    public MessageDto(Long id, String username, String displayName, String content, Instant sentAt, String roomName) {
        this.id = id;
        this.username = username;
        this.displayName = displayName;
        this.content = content;
        this.sentAt = sentAt;
        this.roomName = roomName;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public Instant getSentAt() { return sentAt; }
    public void setSentAt(Instant sentAt) { this.sentAt = sentAt; }
    public String getRoomName() { return roomName; }
    public void setRoomName(String roomName) { this.roomName = roomName; }
}

