package com.privatechat.operations;

import com.privatechat.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static com.privatechat.operations.OperationsModels.*;

@RestController
@RequestMapping("/api/v2")
class OperationsController {
    private final OperationsAccess access;
    private final DeviceStore devices;
    private final WorkspaceSessions sessions;
    private final OperationsActivity activity;
    private final LiveConnections live;
    private final UserRepository users;

    OperationsController(OperationsAccess access, DeviceStore devices, WorkspaceSessions sessions,
                         OperationsActivity activity, LiveConnections live, UserRepository users) {
        this.access = access;
        this.devices = devices;
        this.sessions = sessions;
        this.activity = activity;
        this.live = live;
        this.users = users;
    }

    @GetMapping("/identity")
    Identity identity(Principal principal, HttpServletRequest request, jakarta.servlet.http.HttpServletResponse response) {
        Actor actor = access.actor(principal.getName());
        Device device = access.currentDevice(actor, request);
        // Renew a pre-freeze /api/v2-scoped device cookie at the root path so the
        // retained classic APIs and WebSocket handshake enforce the same approval.
        if (device != null && request.getCookies() != null) {
            for (jakarta.servlet.http.Cookie cookie : request.getCookies()) {
                if (OperationsAccess.COOKIE.equals(cookie.getName()) && cookie.getValue().matches("[A-Za-z0-9_-]{43}")) {
                    response.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from(OperationsAccess.COOKIE, cookie.getValue())
                            .path("/").secure(request.isSecure()).httpOnly(true).sameSite("Lax")
                            .maxAge(Duration.ofDays(180)).build().toString());
                    break;
                }
            }
        }
        if (device != null && device.status() == DeviceStatus.REVOKED) {
            return new Identity(actor.username(), actor.role(), device, null, false);
        }
        return new Identity(actor.username(), actor.role(), device, observe(actor, device, request),
                actor.role() == Role.HOST && device != null && device.status() == DeviceStatus.PENDING
                        && !devices.hasApprovedBefore(actor.userId()));
    }

    @GetMapping("/csrf")
    Map<String, String> csrf(CsrfToken token) {
        return Map.of("headerName", token.getHeaderName(), "token", token.getToken());
    }

    record DeviceRequest(String name) {}

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<Map<String, String>> invalidBody() {
        return ResponseEntity.badRequest().body(Map.of("error", "Invalid request body"));
    }

    @PostMapping("/devices")
    ResponseEntity<Device> register(Principal principal, HttpServletRequest request, @RequestBody DeviceRequest body) {
        Actor actor = access.actor(principal.getName());
        Device current = access.currentDevice(actor, request);
        observe(actor, current, request);
        Registration registration = access.register(actor, current, body.name());
        observe(actor, registration.device(), request);
        activity.record(EventType.DEVICE_REGISTERED, actor.userId(), registration.device().id());
        // Scope the credential to v2. It is carried on the socket upgrade as well as REST.
        String cookie = ResponseCookie.from(OperationsAccess.COOKIE, registration.credential())
                .path("/").secure(request.isSecure()).httpOnly(true).sameSite("Lax")
                .maxAge(Duration.ofDays(180)).build().toString();
        return ResponseEntity.status(HttpStatus.CREATED).header(HttpHeaders.SET_COOKIE, cookie)
                .body(registration.device());
    }

    @PostMapping("/devices/bootstrap-host")
    Device bootstrapHost(Principal principal, HttpServletRequest request) {
        Actor actor = access.actor(principal.getName());
        Device current = access.currentDevice(actor, request);
        if (actor.role() != Role.HOST || current == null) {
            throw new OperationsException(HttpStatus.FORBIDDEN, "Enrolled host account required");
        }
        observe(actor, current, request);
        if (!devices.approveInitialHost(current.id(), actor.userId())) {
            throw new OperationsException(HttpStatus.CONFLICT, "Initial host enrollment is unavailable");
        }
        activity.record(EventType.DEVICE_APPROVED, actor.userId(), current.id());
        return device(current.id());
    }

    @GetMapping("/host/dashboard")
    Dashboard host(Principal principal, HttpServletRequest request) {
        requireHost(principal, request);
        List<AppSession> active = sessions.active();
        List<UserSummary> roster = users.findByIsActiveTrue().stream()
                .map(user -> new UserSummary(user.getId(), user.getUsername(), user.getDisplayName())).toList();
        return new Dashboard(roster, states(devices.all(), active), active, activity.recent());
    }

    @PostMapping("/host/devices/{id}/approve")
    Device approve(Principal principal, HttpServletRequest request, @PathVariable String id) {
        requireHost(principal, request);
        Device target = device(id);
        if (!devices.approve(id)) {
            throw new OperationsException(HttpStatus.CONFLICT, "Only pending devices can be approved");
        }
        activity.record(EventType.DEVICE_APPROVED, target.userId(), id);
        return device(id);
    }

    @PostMapping("/host/devices/{id}/revoke")
    Device revoke(Principal principal, HttpServletRequest request, @PathVariable String id) {
        requireHost(principal, request);
        Device target = device(id);
        Device current = access.currentDevice(access.actor(principal.getName()), request);
        if (current != null && current.id().equals(id)) {
            throw new OperationsException(HttpStatus.CONFLICT, "Use another approved host browser to revoke this one");
        }
        if (!devices.revoke(id)) {
            throw new OperationsException(HttpStatus.CONFLICT, "Device is already revoked");
        }
        sessions.revokeDevice(id);
        activity.record(EventType.DEVICE_REVOKED, target.userId(), id);
        return device(id);
    }

    @PostMapping("/host/sessions/{id}/terminate")
    Map<String, Boolean> terminate(Principal principal, HttpServletRequest request, @PathVariable String id) {
        requireHost(principal, request);
        sessions.terminate(id, request.getSession(false).getId());
        return Map.of("terminated", true);
    }

    @GetMapping("/network")
    Topology topology(Principal principal, HttpServletRequest request) {
        Actor actor = access.actor(principal.getName());
        Device device = access.currentDevice(actor, request);
        if (device != null && device.status() == DeviceStatus.REVOKED) {
            throw new OperationsException(HttpStatus.FORBIDDEN, "Device has been revoked");
        }
        observe(actor, device, request);
        boolean hostView = actor.role() == Role.HOST && device != null && device.status() == DeviceStatus.APPROVED;
        List<Device> visible = hostView ? devices.all().stream()
                .filter(entry -> entry.status() == DeviceStatus.APPROVED).toList()
                : device != null && device.status() == DeviceStatus.APPROVED ? List.of(device) : List.of();
        return new Topology(states(visible, sessions.active()), "UNKNOWN");
    }

    private List<DeviceStatusView> states(List<Device> visible, List<AppSession> active) {
        Set<String> authenticated = active.stream().map(AppSession::deviceId).filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        return visible.stream().map(device -> {
            var connected = device.status() == DeviceStatus.APPROVED
                    ? live.connectedAt(device.id()) : java.util.Optional.<java.time.Instant>empty();
            return new DeviceStatusView(device, connected.isPresent(), authenticated.contains(device.id()),
                    connected.orElse(null));
        }).toList();
    }

    private Device device(String id) {
        return devices.byId(id).orElseThrow(() -> new OperationsException(HttpStatus.NOT_FOUND, "Device unavailable"));
    }

    private void requireHost(Principal principal, HttpServletRequest request) {
        Actor actor = access.actor(principal.getName());
        Device device = access.currentDevice(actor, request);
        access.requireHost(actor, device);
        observe(actor, device, request);
    }

    private AppSession observe(Actor actor, Device device, HttpServletRequest request) {
        return sessions.observe(request.getSession(false), actor, device);
    }
}
