package com.privatechat.operations;

import com.privatechat.model.User;
import com.privatechat.repository.UserRepository;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import java.util.regex.Pattern;

import static com.privatechat.operations.OperationsModels.*;

@Service
public class OperationsAccess {
    static final String COOKIE = "QVA_DEVICE";
    private static final Pattern CREDENTIAL = Pattern.compile("[A-Za-z0-9_-]{43}");
    private final SecureRandom random = new SecureRandom();
    private final UserRepository users;
    private final DeviceStore devices;

    OperationsAccess(UserRepository users, DeviceStore devices) {
        this.users = users;
        this.devices = devices;
    }

    Actor actor(String name) {
        return users.findByUsernameIgnoreCase(name).filter(User::isActive)
                .map(user -> {
                    String grant = devices.grantId(user.getId());
                    return new Actor(user.getId(), user.getUsername(),
                            grant == null ? Role.MEMBER : Role.HOST, grant);
                })
                .orElseThrow(() -> new OperationsException(HttpStatus.FORBIDDEN, "Account unavailable"));
    }

    public boolean isHostRequest(String username, HttpServletRequest request) {
        if (username == null) return false;
        try {
            Actor actor = actor(username);
            Device device = currentDevice(actor, request);
            return actor.role() == Role.HOST && (device != null && device.status() == DeviceStatus.APPROVED);
        } catch (OperationsException denied) {
            return false;
        }
    }

    public boolean isApprovedDeviceRequest(String username, HttpServletRequest request) {
        if (username == null) return false;
        try {
            Actor actor = actor(username);
            Device device = currentDevice(actor, request);
            return device != null && device.status() == DeviceStatus.APPROVED;
        } catch (OperationsException denied) {
            return false;
        }
    }

    void requireHost(Actor actor, Device device) {
        if (!devices.isHost(actor.userId()) || (device == null || device.status() != DeviceStatus.APPROVED)) {
            throw new OperationsException(HttpStatus.FORBIDDEN, "Host access required");
        }
    }

    Device currentDevice(Actor actor, HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return null;
        for (Cookie cookie : cookies) {
            if (COOKIE.equals(cookie.getName()) && CREDENTIAL.matcher(cookie.getValue()).matches()) {
                return devices.byCredential(actor.userId(), digest(cookie.getValue())).orElse(null);
            }
        }
        return null;
    }

    Registration register(Actor actor, Device current, String suppliedName) {
        if (current != null) {
            throw new OperationsException(HttpStatus.CONFLICT, "This browser already has a device record");
        }
        String name = suppliedName == null ? "" : suppliedName.strip();
        if (name.isEmpty() || name.length() > 60 || name.chars().anyMatch(Character::isISOControl)) {
            throw new OperationsException(HttpStatus.BAD_REQUEST, "Enter a device name of 1–60 characters");
        }
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String credential = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        String id = UUID.randomUUID().toString();
        if (!devices.register(id, actor.userId(), name, digest(credential), System.currentTimeMillis())) {
            throw new OperationsException(HttpStatus.CONFLICT, "This account has reached its device registration limit");
        }
        return new Registration(devices.byId(id).orElseThrow(), credential);
    }

    private static String digest(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.US_ASCII)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
