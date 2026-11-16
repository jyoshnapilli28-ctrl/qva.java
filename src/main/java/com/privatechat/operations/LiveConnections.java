package com.privatechat.operations;

import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static com.privatechat.operations.OperationsModels.*;

@Component
class LiveConnections {
    static final long STALE_MS = 35_000;
    private static final long MIN_HEARTBEAT_MS = 2_000;
    record Binding(long userId, String deviceId, String sessionId) {}
    private record Connection(WebSocketSession socket, Binding binding, Instant connectedAt,
                              AtomicLong lastHeartbeat) {}
    private final Map<String, Connection> connections = new HashMap<>();
    private final DeviceStore devices;
    private final WorkspaceSessions sessions;
    private final OperationsActivity activity;
    private final ObjectMapper json;

    LiveConnections(DeviceStore devices, WorkspaceSessions sessions, OperationsActivity activity, ObjectMapper json) {
        this.devices = devices;
        this.sessions = sessions;
        this.activity = activity;
        this.json = json;
    }

    boolean valid(Binding binding) {
        return sessions.isActive(binding.sessionId()) && devices.byId(binding.deviceId())
                .filter(device -> device.userId() == binding.userId() && device.status() == DeviceStatus.APPROVED)
                .isPresent();
    }

    void attach(WebSocketSession socket, Binding binding) throws IOException {
        if (binding == null || !valid(binding)) {
            socket.close(CloseStatus.POLICY_VIOLATION);
            return;
        }
        socket.setTextMessageSizeLimit(64);
        Instant now = Instant.now();
        Connection connection = new Connection(new ConcurrentWebSocketSessionDecorator(socket, 2_000, 16_384),
                binding, now, new AtomicLong(now.toEpochMilli()));
        boolean first;
        synchronized (connections) {
            if (connections.size() >= 64) {
                socket.close(CloseStatus.POLICY_VIOLATION);
                return;
            }
            first = connections.values().stream().noneMatch(c -> c.binding().deviceId().equals(binding.deviceId()));
            connections.put(socket.getId(), connection);
        }
        if (!valid(binding)) { close(connection, null, CloseStatus.POLICY_VIOLATION); return; }
        devices.seen(binding.deviceId(), now.toEpochMilli());
        if (first) activity.record(EventType.DEVICE_APP_CONNECTED, binding.userId(), binding.deviceId());
    }

    void heartbeat(WebSocketSession socket, String payload) {
        Connection connection;
        synchronized (connections) { connection = connections.get(socket.getId()); }
        if (connection == null) return;
        if (!"heartbeat".equals(payload)) {
            close(connection, null, CloseStatus.POLICY_VIOLATION);
            return;
        }
        long now = System.currentTimeMillis();
        long previous = connection.lastHeartbeat().get();
        if (now - previous < MIN_HEARTBEAT_MS) return;
        Binding binding = connection.binding();
        if (now - previous >= STALE_MS || !valid(binding)) {
            close(connection, now - previous >= STALE_MS ? EventType.CONNECTION_STALE : null,
                    CloseStatus.POLICY_VIOLATION);
            return;
        }
        if (!connection.lastHeartbeat().compareAndSet(previous, now)) return;
        devices.seen(binding.deviceId(), now);
        activity.record(EventType.HEARTBEAT_RECEIVED, binding.userId(), binding.deviceId());
    }

    void detach(String id, EventType reason) {
        Connection removed;
        boolean last;
        synchronized (connections) {
            removed = connections.remove(id);
            if (removed == null) return;
            last = connections.values().stream().noneMatch(c -> c.binding().deviceId().equals(removed.binding().deviceId()));
        }
        Binding binding = removed.binding();
        if (reason != null) activity.record(reason, binding.userId(), binding.deviceId());
        if (last) activity.record(EventType.DEVICE_APP_DISCONNECTED, binding.userId(), binding.deviceId());
    }

    Optional<Instant> connectedAt(String deviceId) {
        return copy().stream()
                .filter(c -> c.binding().deviceId().equals(deviceId) && c.socket().isOpen()
                        && sessions.isActive(c.binding().sessionId())
                        && System.currentTimeMillis() - c.lastHeartbeat().get() < STALE_MS)
                .map(Connection::connectedAt).min(Instant::compareTo);
    }

    @Scheduled(fixedDelay = 2_000)
    void removeStale() {
        long now = System.currentTimeMillis();
        for (Connection connection : copy()) {
            if (now - connection.lastHeartbeat().get() >= STALE_MS) {
                close(connection, EventType.CONNECTION_STALE, CloseStatus.GOING_AWAY);
            } else if (!valid(connection.binding())) {
                close(connection, null, CloseStatus.POLICY_VIOLATION);
            }
        }
    }

    @EventListener
    void broadcast(Event event) {
        for (Connection connection : copy()) {
            Binding binding = connection.binding();
            if (!valid(binding)) {
                close(connection, null, CloseStatus.POLICY_VIOLATION);
                continue;
            }
            if (devices.isHost(binding.userId()) || binding.deviceId().equals(event.deviceId())) {
                send(connection, event);
            }
        }
    }

    private void send(Connection connection, Event event) {
        try {
            connection.socket().sendMessage(new TextMessage(json.writeValueAsString(event)));
        } catch (IOException | RuntimeException disconnected) {
            close(connection, null, CloseStatus.SERVER_ERROR);
        }
    }

    private List<Connection> copy() {
        synchronized (connections) { return List.copyOf(connections.values()); }
    }

    private void close(Connection connection, EventType reason, CloseStatus status) {
        detach(connection.socket().getId(), reason);
        try { connection.socket().close(status); }
        catch (IOException ignored) { /* The peer may have already disconnected. */ }
    }
}
