package com.privatechat.operations;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.io.IOException;
import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.http.HttpStatus.*;

@RestController
@RequestMapping("/api/v2/conversations")
class MessagingController {
    private static final int MAX_ENVELOPE_BYTES = 16_384;
    private final boolean enabled;
    private final OperationsAccess access;
    private final WorkspaceSessions sessions;
    private final DirectMessageStore messages;
    private final MessageLifecycle lifecycle;

    MessagingController(OperationsAccess access, WorkspaceSessions sessions, DirectMessageStore messages, MessageLifecycle lifecycle, @Value("${qva.messaging.enabled:false}") boolean enabled) {
        this.enabled = enabled;
        this.access = access;
        this.sessions = sessions;
        this.messages = messages;
        this.lifecycle = lifecycle;
    }

    record DirectRequest(long peerId) {}
    record Sender(long userId, String deviceId, OperationsModels.Role role) {}
    record GroupRequest(String name) {}
    record ParticipantRequest(long userId) {}
    record AckRequest(String state) {}

    @ExceptionHandler({HttpMessageNotReadableException.class, MissingRequestHeaderException.class,
            MethodArgumentTypeMismatchException.class})
    ResponseEntity<Map<String, String>> malformedRequest() {
        return ResponseEntity.badRequest().body(Map.of("error", "Invalid request body"));
    }

    @GetMapping("/status")
    Map<String, Boolean> status() { return Map.of("enabled", enabled); }

    @GetMapping
    List<DirectMessageStore.Conversation> list(Principal principal, HttpServletRequest request) {
        return messages.list(sender(principal, request).userId());
    }

    @PostMapping("/direct")
    DirectMessageStore.Conversation open(Principal principal, HttpServletRequest request,
                                          @RequestBody DirectRequest body) {
        return messages.open(sender(principal, request).userId(), body.peerId());
    }

    @GetMapping("/groups")
    List<MessageLifecycle.Group> groups(Principal principal, HttpServletRequest request) {
        return lifecycle.groups(sender(principal, request).userId());
    }

    @PostMapping("/groups")
    MessageLifecycle.Group group(Principal principal, HttpServletRequest request, @RequestBody GroupRequest body) {
        var current = sender(principal, request);
        if (current.role() != OperationsModels.Role.HOST) {
            throw new OperationsException(FORBIDDEN, "Only a host can create a group");
        }
        return lifecycle.create(current.userId(), body.name());
    }

    @GetMapping("/groups/{id}/participants")
    List<MessageLifecycle.Participant> participants(Principal principal, HttpServletRequest request, @PathVariable UUID id) {
        return lifecycle.participants(sender(principal, request).userId(), id.toString());
    }

    @PostMapping("/groups/{id}/participants")
    Map<String, Boolean> add(Principal principal, HttpServletRequest request, @PathVariable UUID id,
                             @RequestBody ParticipantRequest body) {
        lifecycle.add(sender(principal, request).userId(), id.toString(), body.userId());
        return Map.of("updated", true);
    }

    @DeleteMapping("/groups/{id}/participants/{userId}")
    Map<String, Boolean> remove(Principal principal, HttpServletRequest request, @PathVariable UUID id,
                                @PathVariable long userId) {
        lifecycle.remove(sender(principal, request).userId(), id.toString(), userId);
        return Map.of("updated", true);
    }

    @GetMapping("/{id}/envelopes")
    List<DirectMessageStore.Envelope> history(Principal principal, HttpServletRequest request,
            @PathVariable UUID id, @RequestParam(defaultValue = "9223372036854775807") long before,
            @RequestParam(defaultValue = "0") long after) {
        if (before <= 0 || after < 0 || after >= before) throw new OperationsException(BAD_REQUEST, "Invalid cursor");
        long user = sender(principal, request).userId();
        String conversation = id.toString();
        var membership = lifecycle.membership(user, conversation);
        return membership.group() ? lifecycle.history(user, conversation, membership, before, after)
                : messages.history(user, conversation, before, after);
    }

    @PostMapping("/{id}/envelopes/{sequence}/receipt")
    MessageLifecycle.Receipt acknowledge(Principal principal, HttpServletRequest request,
            @PathVariable UUID id, @PathVariable long sequence, @RequestBody AckRequest body) {
        var current = sender(principal, request);
        if (!"READ".equals(body.state()) && !"DELIVERED".equals(body.state())) {
            throw new OperationsException(BAD_REQUEST, "Invalid receipt state");
        }
        return lifecycle.acknowledge(current.userId(), current.deviceId(), id.toString(), sequence,
                "READ".equals(body.state()));
    }

    @GetMapping("/{id}/envelopes/{sequence}/receipt")
    List<MessageLifecycle.Receipt> receipts(Principal principal, HttpServletRequest request,
            @PathVariable UUID id, @PathVariable long sequence) {
        return lifecycle.receipts(sender(principal, request).userId(), id.toString(), sequence);
    }

    @PostMapping(path = "/{id}/envelopes", consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    DirectMessageStore.Envelope submit(Principal principal, HttpServletRequest request, @PathVariable UUID id,
            @RequestHeader("Idempotency-Key") UUID requestId, @RequestHeader("QVA-Envelope-Version") int version)
            throws IOException {
        Sender sender = sender(principal, request);
        if (version != 1) throw new OperationsException(BAD_REQUEST, "Unsupported envelope format");
        if (request.getContentLengthLong() > MAX_ENVELOPE_BYTES) {
            throw new OperationsException(PAYLOAD_TOO_LARGE, "Envelope too large");
        }
        byte[] envelope = request.getInputStream().readNBytes(MAX_ENVELOPE_BYTES + 1);
        if (envelope.length > MAX_ENVELOPE_BYTES) {
            throw new OperationsException(PAYLOAD_TOO_LARGE, "Envelope too large");
        }
        if (envelope.length == 0) throw new OperationsException(BAD_REQUEST, "Envelope is empty");
        if (lifecycle.membership(sender.userId(), id.toString()).group()) {
            return lifecycle.submit(sender.userId(), sender.deviceId(), id.toString(), requestId.toString(), version, envelope);
        }
        return messages.submit(sender.userId(), sender.deviceId(), id.toString(), requestId.toString(),
                version, envelope);
    }

    private Sender sender(Principal principal, HttpServletRequest request) {
        if (!enabled) throw new OperationsException(SERVICE_UNAVAILABLE, "Messaging is disabled");
        var actor = access.actor(principal.getName());
        var device = access.currentDevice(actor, request);
        if (device == null || device.status() != OperationsModels.DeviceStatus.APPROVED) {
            throw new OperationsException(FORBIDDEN, "An approved workspace device is required");
        }
        sessions.observe(request.getSession(false), actor, device);
        return new Sender(actor.userId(), device.id(), actor.role());
    }
}
