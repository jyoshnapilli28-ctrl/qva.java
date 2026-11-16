package com.privatechat.operations;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.List;
import java.util.UUID;

import static com.privatechat.operations.OperationsModels.*;

@Component
class OperationsActivity {
    private static final int MAX_EVENTS = 200;
    private final ArrayDeque<Event> recent = new ArrayDeque<>();
    private final ApplicationEventPublisher publisher;

    OperationsActivity(ApplicationEventPublisher publisher) { this.publisher = publisher; }

    void record(EventType type, Long userId, String deviceId) {
        Event event = new Event(UUID.randomUUID().toString(), Instant.now(), type, userId, deviceId, "APPLICATION");
        synchronized (recent) {
            recent.addFirst(event);
            if (recent.size() > MAX_EVENTS) recent.removeLast();
        }
        publisher.publishEvent(event);
    }

    List<Event> recent() {
        synchronized (recent) {
            return recent.stream().filter(event -> event.type() != EventType.HEARTBEAT_RECEIVED)
                    .limit(40).toList();
        }
    }
}
