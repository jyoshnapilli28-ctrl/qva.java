package com.privatechat.config;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.time.Instant;

/**
 * JPA AttributeConverter for storing {@link Instant} values in SQLite.
 *
 * <p>SQLite has no native TIMESTAMP type. We store instants as ISO-8601
 * strings in the format {@code yyyy-MM-dd HH:mm:ss} (no 'T', no 'Z')
 * because that is what the SQLite JDBC driver can parse back when the
 * column type is declared as TEXT.
 *
 * <p>Stored value example: {@code 2026-11-01 13:14:12}
 */
@Converter(autoApply = true)
public class InstantConverter implements AttributeConverter<Instant, String> {

    private static final java.time.format.DateTimeFormatter FORMATTER =
            java.time.format.DateTimeFormatter
                    .ofPattern("yyyy-MM-dd HH:mm:ss")
                    .withZone(java.time.ZoneOffset.UTC);

    @Override
    public String convertToDatabaseColumn(Instant instant) {
        if (instant == null) return null;
        return FORMATTER.format(instant);
    }

    @Override
    public Instant convertToEntityAttribute(String dbValue) {
        if (dbValue == null || dbValue.isBlank()) return null;
        try {
            // Handle both formats: with 'T'/'Z' and without
            if (dbValue.contains("T")) {
                // e.g. "2026-11-01T13:14:12Z" from Flyway seed
                return Instant.parse(dbValue.endsWith("Z") ? dbValue : dbValue + "Z");
            }
            return Instant.from(FORMATTER.parse(dbValue));
        } catch (Exception e) {
            // Fallback: try parsing as epoch millis
            try { return Instant.ofEpochMilli(Long.parseLong(dbValue)); }
            catch (NumberFormatException ex) { return null; }
        }
    }
}

