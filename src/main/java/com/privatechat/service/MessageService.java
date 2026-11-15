package com.privatechat.service;

import com.privatechat.dto.MessageDto;
import com.privatechat.exception.ChatException;
import com.privatechat.model.ChatRoom;
import com.privatechat.model.Message;
import com.privatechat.model.User;
import com.privatechat.repository.ChatRoomRepository;
import com.privatechat.repository.MessageRepository;
import com.privatechat.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Core service for storing and broadcasting chat messages.
 */
@Service
public class MessageService {

    private static final Logger log = LoggerFactory.getLogger(MessageService.class);

    @Value("${chat.message.max-length:2000}")
    private int maxMessageLength;

    @Value("${chat.history.default-limit:100}")
    private int defaultHistoryLimit;

    private final MessageRepository messageRepository;
    private final UserRepository userRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final SimpMessagingTemplate messagingTemplate;

    public MessageService(
            MessageRepository messageRepository,
            UserRepository userRepository,
            ChatRoomRepository chatRoomRepository,
            SimpMessagingTemplate messagingTemplate) {
        this.messageRepository = messageRepository;
        this.userRepository = userRepository;
        this.chatRoomRepository = chatRoomRepository;
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * Persists and broadcasts a new message.
     */
    @Transactional
    public MessageDto sendMessage(String username, String roomName, String content) {
        // Validate content
        if (!StringUtils.hasText(content)) {
            throw new ChatException("Message content cannot be empty");
        }
        String trimmed = content.trim();
        if (trimmed.length() > maxMessageLength) {
            throw new ChatException("Message exceeds maximum length of " + maxMessageLength + " characters");
        }

        User user = userRepository.findByUsernameIgnoreCase(username)
                .orElseThrow(() -> new ChatException("User not found: " + username));

        ChatRoom room = chatRoomRepository.findByName(roomName)
                .orElseThrow(() -> new ChatException("Chat room not found: " + roomName));

        Message message = new Message(room, user, trimmed);
        message = messageRepository.save(message);

        MessageDto dto = toDto(message);

        // Broadcast to all subscribers of the room topic
        messagingTemplate.convertAndSend("/topic/messages/" + roomName, dto);
        log.debug("Message #{} from {} in room '{}' saved and broadcast", message.getId(), username, roomName);

        return dto;
    }

    /**
     * Retrieves all messages for a room (up to default limit), ordered oldest-first.
     */
    @Transactional(readOnly = true)
    public List<MessageDto> getHistory(String roomName) {
        return getHistory(roomName, defaultHistoryLimit);
    }

    /**
     * Retrieves the most recent N messages for a room, returned oldest-first.
     */
    @Transactional(readOnly = true)
    public List<MessageDto> getHistory(String roomName, int limit) {
        ChatRoom room = chatRoomRepository.findByName(roomName)
                .orElseThrow(() -> new ChatException("Chat room not found: " + roomName));

        // Fetch most recent messages in descending order, then reverse for display
        List<Message> messages = messageRepository.findRecentByRoomId(room.getId(),
                PageRequest.of(0, limit));

        // Reverse to get chronological order
        List<Message> chronological = messages.stream()
                .collect(Collectors.collectingAndThen(Collectors.toList(), list -> {
                    java.util.Collections.reverse(list);
                    return list;
                }));

        return chronological.stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    private MessageDto toDto(Message m) {
        return new MessageDto(
                m.getId(),
                m.getUser().getUsername(),
                m.getUser().getDisplayName(),
                m.getContent(),
                m.getSentAt(),
                m.getRoom().getName()
        );
    }
}

