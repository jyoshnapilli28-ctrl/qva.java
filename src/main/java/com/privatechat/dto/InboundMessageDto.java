package com.privatechat.dto;

/**
 * Inbound message payload from clients over WebSocket.
 */
public class InboundMessageDto {

    private String content;
    private String roomName;

    public InboundMessageDto() {}

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getRoomName() { return roomName; }
    public void setRoomName(String roomName) { this.roomName = roomName; }
}

