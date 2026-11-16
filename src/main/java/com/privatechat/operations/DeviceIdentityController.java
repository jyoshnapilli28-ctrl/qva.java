package com.privatechat.operations;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.io.IOException;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.GeneralSecurityException;
import java.security.Principal;
import java.security.spec.X509EncodedKeySpec;
import java.util.Map;
import java.util.UUID;

import static org.springframework.http.HttpStatus.*;
import static org.springframework.http.MediaType.APPLICATION_OCTET_STREAM;

@RestController
@RequestMapping("/api/v2/device-identities")
class DeviceIdentityController {
    private static final int SIGNING_KEY_BYTES = 44;
    private static final int MAX_PACKAGE_BYTES = 16_384;

    private final OperationsAccess access;
    private final WorkspaceSessions sessions;
    private final DeviceIdentityStore identities;
    private final boolean enabled;

    DeviceIdentityController(OperationsAccess access, WorkspaceSessions sessions, DeviceIdentityStore identities,
            @Value("${qva.device-identity.enabled:false}") boolean enabled) {
        this.access = access;
        this.sessions = sessions;
        this.identities = identities;
        this.enabled = enabled;
    }

    @ExceptionHandler({MethodArgumentTypeMismatchException.class, MissingRequestHeaderException.class})
    ResponseEntity<Map<String, String>> malformedRequest() {
        return response(BAD_REQUEST, Map.of("error", "Invalid identity request"));
    }

    @GetMapping("/me")
    ResponseEntity<DeviceIdentityStore.Identity> own(Principal principal, HttpServletRequest request) {
        return response(OK, identities.own(device(principal, request)));
    }

    @PutMapping("/me/credentials/{expectedVersion}")
    ResponseEntity<DeviceIdentityStore.Identity> register(Principal principal, HttpServletRequest request,
            @PathVariable int expectedVersion) throws IOException {
        var owner = device(principal, request);
        if (expectedVersion < 0 || expectedVersion == Integer.MAX_VALUE) {
            throw new OperationsException(BAD_REQUEST, "Invalid credential version");
        }
        byte[] key = bytes(request, SIGNING_KEY_BYTES);
        validatePublicKey(key);
        return response(CREATED, identities.register(owner, expectedVersion, key));
    }

    @GetMapping("/conversations/{conversation}/devices/{peer}")
    ResponseEntity<DeviceIdentityStore.Identity> peer(Principal principal, HttpServletRequest request,
            @PathVariable UUID conversation, @PathVariable UUID peer) {
        return response(OK, identities.peer(device(principal, request), conversation, peer));
    }

    @PutMapping("/conversations/{conversation}/devices/{peer}/verification/{version}")
    ResponseEntity<DeviceIdentityStore.Identity> verify(Principal principal, HttpServletRequest request,
            @PathVariable UUID conversation, @PathVariable UUID peer, @PathVariable int version,
            @RequestHeader("QVA-Identity-Fingerprint") String fingerprint) {
        var owner = device(principal, request);
        if (version <= 0 || !fingerprint.matches("[a-f0-9]{64}")) {
            throw new OperationsException(BAD_REQUEST, "Invalid credential verification");
        }
        return response(OK, identities.verify(owner, conversation, peer, version, fingerprint));
    }

    @PostMapping("/me/credentials/{version}/key-packages")
    ResponseEntity<DeviceIdentityStore.Publication> publish(Principal principal, HttpServletRequest request,
            @PathVariable int version) throws IOException {
        var owner = device(principal, request);
        if (version <= 0) throw new OperationsException(BAD_REQUEST, "Invalid credential version");
        return response(CREATED, identities.publish(owner, version, bytes(request, MAX_PACKAGE_BYTES)));
    }

    @PostMapping("/conversations/{conversation}/devices/{peer}/key-packages/{version}/claim")
    ResponseEntity<DeviceIdentityStore.Claim> claim(Principal principal, HttpServletRequest request,
            @PathVariable UUID conversation, @PathVariable UUID peer, @PathVariable int version,
            @RequestHeader("Idempotency-Key") UUID claimId) {
        var owner = device(principal, request);
        if (version <= 0) throw new OperationsException(BAD_REQUEST, "Invalid credential version");
        return response(OK, identities.claim(owner, conversation, peer, version, claimId));
    }

    private OperationsModels.Device device(Principal principal, HttpServletRequest request) {
        if (!enabled) throw new OperationsException(SERVICE_UNAVAILABLE, "Device identity service is disabled");
        var actor = access.actor(principal.getName());
        var device = access.currentDevice(actor, request);
        if (device == null || device.status() != OperationsModels.DeviceStatus.APPROVED) {
            throw new OperationsException(FORBIDDEN, "An approved workspace device is required");
        }
        sessions.observe(request.getSession(false), actor, device);
        return device;
    }

    private static void validatePublicKey(byte[] bytes) {
        try {
            if (bytes.length == SIGNING_KEY_BYTES) {
                var key = KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(bytes));
                if (MessageDigest.isEqual(key.getEncoded(), bytes)) return;
            }
        } catch (GeneralSecurityException invalid) {
            throw new OperationsException(BAD_REQUEST, "Expected a canonical Ed25519 public key");
        }
        throw new OperationsException(BAD_REQUEST, "Expected a canonical Ed25519 public key");
    }

    private static byte[] bytes(HttpServletRequest request, int maximum) throws IOException {
        try {
            String type = request.getContentType();
            if (type == null || !APPLICATION_OCTET_STREAM.includes(MediaType.parseMediaType(type))) {
                throw new OperationsException(UNSUPPORTED_MEDIA_TYPE, "Expected application/octet-stream");
            }
        } catch (InvalidMediaTypeException invalid) {
            throw new OperationsException(UNSUPPORTED_MEDIA_TYPE, "Expected application/octet-stream");
        }
        if (request.getContentLengthLong() > maximum) throw new OperationsException(PAYLOAD_TOO_LARGE, "Payload too large");
        byte[] bytes = request.getInputStream().readNBytes(maximum + 1);
        if (bytes.length > maximum) throw new OperationsException(PAYLOAD_TOO_LARGE, "Payload too large");
        if (bytes.length == 0) throw new OperationsException(BAD_REQUEST, "Empty payload");
        return bytes;
    }

    private static <T> ResponseEntity<T> response(org.springframework.http.HttpStatus status, T body) {
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(body);
    }
}
