package com.privatechat.operations;

import jakarta.servlet.http.HttpSession;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.web.session.HttpSessionDestroyedEvent;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.privatechat.operations.OperationsModels.*;

@Component
public class WorkspaceSessions {
    private final ConcurrentHashMap<String, AppSession> tracked = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> hostGrants = new ConcurrentHashMap<>();
    private final SessionRegistry registry;
    private final DeviceStore devices;
    private final OperationsActivity activity;

    WorkspaceSessions(SessionRegistry registry, OperationsActivity activity, DeviceStore devices) {
        this.registry = registry;
        this.activity = activity;
        this.devices = devices;
    }

    AppSession observe(HttpSession session, Actor actor, Device device) {
        if (session == null || !isActive(session.getId())) {
            throw new OperationsException(HttpStatus.UNAUTHORIZED, "Session expired");
        }
        String servletId = session.getId();
        if (tracked.containsKey(servletId) && !java.util.Objects.equals(hostGrants.get(servletId), actor.grantId())) {
            expire(servletId, EventType.SESSION_EXPIRED);
            throw new OperationsException(HttpStatus.UNAUTHORIZED, "Session authorization changed");
        }
        AtomicBoolean first = new AtomicBoolean();
        String deviceId = device == null ? null : device.id();
        AppSession trackedSession = tracked.compute(session.getId(), (key, existing) -> {
            if (existing == null) {
                if (actor.grantId() != null) hostGrants.put(key, actor.grantId());
                first.set(true);
                return new AppSession(UUID.randomUUID().toString(), actor.userId(), actor.username(), deviceId, Instant.now());
            }
            if (existing.userId() != actor.userId()
                    || existing.deviceId() != null && !existing.deviceId().equals(deviceId)) {
                throw new OperationsException(HttpStatus.FORBIDDEN, "Session is bound to another device");
            }
            return new AppSession(existing.id(), existing.userId(), existing.username(), deviceId, existing.startedAt());
        });
        if (first.get()) activity.record(EventType.USER_AUTHENTICATED, actor.userId(), deviceId);
        return trackedSession;
    }

    boolean isActive(String servletSessionId) {
        SessionInformation information = registry.getSessionInformation(servletSessionId);
        if (information == null || information.isExpired()) return false;
        AppSession observed = tracked.get(servletSessionId);
        String grant = hostGrants.get(servletSessionId);
        if (observed != null && grant != null && !grant.equals(devices.grantId(observed.userId()))) {
            information.expireNow();
            return false;
        }
        return true;
    }

    List<AppSession> active() {
        return tracked.entrySet().stream().filter(entry -> isActive(entry.getKey()))
                .map(java.util.Map.Entry::getValue)
                .sorted(Comparator.comparing(AppSession::startedAt)).toList();
    }

    void terminate(String publicId, String currentServletId) {
        var match = tracked.entrySet().stream()
                .filter(entry -> entry.getValue().id().equals(publicId)).findFirst()
                .orElseThrow(() -> new OperationsException(HttpStatus.NOT_FOUND, "Session unavailable"));
        if (match.getKey().equals(currentServletId)) {
            throw new OperationsException(HttpStatus.CONFLICT, "Use Sign out for your own session");
        }
        expire(match.getKey(), EventType.SESSION_TERMINATED);
    }

    void revokeDevice(String deviceId) {
        tracked.forEach((id, session) -> {
            if (deviceId.equals(session.deviceId())) expire(id, EventType.SESSION_TERMINATED);
        });
    }

    private void expire(String servletSessionId, EventType type) {
        SessionInformation information = registry.getSessionInformation(servletSessionId);
        if (information != null) information.expireNow();
        remove(servletSessionId, type);
    }

    // The existing logout filter invokes its handlers before invalidating the HTTP session.
    public void logout(HttpSession session) {
        if (session != null) remove(session.getId(), EventType.USER_LOGGED_OUT);
    }

    private void remove(String servletSessionId, EventType type) {
        AppSession removed = tracked.remove(servletSessionId);
        hostGrants.remove(servletSessionId);
        if (removed != null) activity.record(type, removed.userId(), removed.deviceId());
    }

    @EventListener
    public void destroyed(HttpSessionDestroyedEvent event) {
        remove(event.getId(), EventType.SESSION_EXPIRED);
    }

    @Scheduled(fixedDelay = 5_000)
    void expireInactive() {
        tracked.keySet().forEach(id -> {
            if (!isActive(id)) remove(id, EventType.SESSION_EXPIRED);
        });
    }
}
