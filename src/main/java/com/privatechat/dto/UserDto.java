package com.privatechat.dto;

import java.time.Instant;

/**
 * Public user profile, safe to expose over the API.
 */
public class UserDto {

    private Long id;
    private String username;
    private String displayName;
    private Instant lastLogin;
    private boolean online;

    public UserDto() {}

    public UserDto(Long id, String username, String displayName, Instant lastLogin, boolean online) {
        this.id = id;
        this.username = username;
        this.displayName = displayName;
        this.lastLogin = lastLogin;
        this.online = online;
    }

    public Long getId() { return id; }
    public String getUsername() { return username; }
    public String getDisplayName() { return displayName; }
    public Instant getLastLogin() { return lastLogin; }
    public boolean isOnline() { return online; }
    public void setOnline(boolean online) { this.online = online; }
}

