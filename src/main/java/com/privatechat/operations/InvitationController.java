package com.privatechat.operations;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.privatechat.operations.InvitationService.*;
import static com.privatechat.operations.OperationsModels.*;

@RestController
class InvitationController {
    private final OperationsAccess access;
    private final WorkspaceSessions sessions;
    private final InvitationService invitations;
    private final RegistrationRateLimiter rateLimiter;

    InvitationController(OperationsAccess access, WorkspaceSessions sessions,
                         InvitationService invitations, RegistrationRateLimiter rateLimiter) {
        this.access = access;
        this.sessions = sessions;
        this.invitations = invitations;
        this.rateLimiter = rateLimiter;
    }

    record CreateRequest(Integer expirationHours) {}
    record RegistrationRequest(String token, String username, String displayName, String password) {}

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<Map<String, String>> invalidBody() {
        return ResponseEntity.badRequest().body(Map.of("error", "Invalid request body"));
    }

    @GetMapping("/api/auth/csrf")
    Map<String, String> csrf(CsrfToken token) {
        return Map.of("headerName", token.getHeaderName(), "token", token.getToken());
    }

    @PostMapping("/api/auth/register")
    ResponseEntity<RegisteredAccount> register(@RequestBody RegistrationRequest body, HttpServletRequest request) {
        String proxyIdentity = request.getHeader("Tailscale-User-Login");
        String key = proxyIdentity != null && proxyIdentity.length() <= 254
                ? "tailscale:" + proxyIdentity.toLowerCase(java.util.Locale.ROOT)
                : "address:" + request.getRemoteAddr();
        if (!rateLimiter.allow(key)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .cacheControl(CacheControl.noStore()).build();
        }
        RegisteredAccount result = invitations.register(body.token(), body.username(), body.displayName(), body.password());
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore()).body(result);
    }

    @GetMapping("/api/v2/host/invitations")
    List<InvitationView> list(Principal principal, HttpServletRequest request) {
        requireHost(principal, request);
        return invitations.list();
    }

    @GetMapping("/api/v2/host/invitations/audit")
    List<InvitationAudit> audit(Principal principal, HttpServletRequest request) {
        requireHost(principal, request);
        return invitations.audit();
    }

    @PostMapping("/api/v2/host/invitations")
    ResponseEntity<CreatedInvitation> create(Principal principal, HttpServletRequest request,
                                             @RequestBody(required = false) CreateRequest body) {
        Actor actor = requireHost(principal, request);
        int hours = body == null || body.expirationHours() == null
                ? DEFAULT_EXPIRATION_HOURS : body.expirationHours();
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore())
                .body(invitations.create(actor.userId(), hours));
    }

    @DeleteMapping("/api/v2/host/invitations/{id}")
    ResponseEntity<Map<String, Boolean>> revoke(Principal principal, HttpServletRequest request, @PathVariable UUID id) {
        Actor actor = requireHost(principal, request);
        invitations.revoke(actor.userId(), id.toString());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Map.of("revoked", true));
    }

    private Actor requireHost(Principal principal, HttpServletRequest request) {
        Actor actor = access.actor(principal.getName());
        Device device = access.currentDevice(actor, request);
        access.requireHost(actor, device);
        sessions.observe(request.getSession(false), actor, device);
        return actor;
    }
}
