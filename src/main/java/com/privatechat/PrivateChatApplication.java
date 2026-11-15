package com.privatechat;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.io.File;

@SpringBootApplication
@EnableScheduling
public class PrivateChatApplication {

    public static void main(String[] args) {
        sanitizeDbPath();
        SpringApplication.run(PrivateChatApplication.class, args);
    }

    /**
     * Sanitizes the DB_PATH system property to prevent Windows quote-wrapping issues
     * and ensures the target directory exists before SQLite and Flyway initialize.
     */
    static void sanitizeDbPath() {
        String dbPath = System.getProperty("DB_PATH");
        if (dbPath != null) {
            String sanitized = dbPath.trim();
            // Remove surrounding double or single quotes if present
            if ((sanitized.startsWith("\"") && sanitized.endsWith("\"")) ||
                (sanitized.startsWith("'") && sanitized.endsWith("'"))) {
                if (sanitized.length() >= 2) {
                    sanitized = sanitized.substring(1, sanitized.length() - 1).trim();
                }
            }
            System.setProperty("DB_PATH", sanitized);
            ensureParentDirectoryExists(sanitized);
        } else {
            ensureParentDirectoryExists("./data/chat.db");
        }
    }

    private static void ensureParentDirectoryExists(String path) {
        try {
            File file = new File(path);
            File parentDir = file.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs();
            }
        } catch (Exception ignored) {
        }
    }
}


