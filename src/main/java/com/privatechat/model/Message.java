package com.privatechat.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "messages")
public class Message {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id", nullable = false)
    private ChatRoom room;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private String content;

    @Column(name = "sent_at", nullable = false, columnDefinition = "TEXT")
    private Instant sentAt;

    @Column(name = "is_deleted", nullable = false)
    private boolean isDeleted = false;

    protected Message() {}

    public Message(ChatRoom room, User user, String content) {
        this.room = room;
        this.user = user;
        this.content = content;
        this.sentAt = Instant.now();
        this.isDeleted = false;
    }

    public Long getId() { return id; }
    public ChatRoom getRoom() { return room; }
    public User getUser() { return user; }
    public String getContent() { return content; }
    public Instant getSentAt() { return sentAt; }
    public boolean isDeleted() { return isDeleted; }
    public void setDeleted(boolean deleted) { isDeleted = deleted; }
}
