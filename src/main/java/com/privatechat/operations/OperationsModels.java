package com.privatechat.operations;

import java.time.Instant;
import java.util.List;

final class OperationsModels {
    private OperationsModels() {}

    enum Role { HOST, MEMBER }
    enum DeviceStatus { PENDING, APPROVED, REVOKED }
    enum EventType {
        USER_AUTHENTICATED, USER_LOGGED_OUT, SESSION_EXPIRED, SESSION_TERMINATED,
        DEVICE_REGISTERED, DEVICE_APPROVED, DEVICE_REVOKED,
        DEVICE_APP_CONNECTED, DEVICE_APP_DISCONNECTED, HEARTBEAT_RECEIVED, CONNECTION_STALE
    }
    record Actor(long userId, String username, Role role, String grantId) {}
    record Device(String id, long userId, String username, String name, DeviceStatus status,
                  Instant registeredAt, Instant lastSeen, Instant approvedAt, Instant revokedAt) {}
    record Registration(Device device, String credential) {}
    record AppSession(String id, long userId, String username, String deviceId, Instant startedAt) {}
    record Event(String id, Instant timestamp, EventType type, Long userId, String deviceId,
                 String source) {}
    record Identity(String username, Role role, Device device, AppSession session, boolean mayBootstrapHost) {}
    record UserSummary(long id, String username, String displayName) {}
    record DeviceStatusView(Device device, boolean connected, boolean authenticated, Instant connectedAt) {}
    record Dashboard(List<UserSummary> users, List<DeviceStatusView> devices,
                     List<AppSession> sessions, List<Event> activity) {}
    record Topology(List<DeviceStatusView> devices, String vpnStatus) {}
}
