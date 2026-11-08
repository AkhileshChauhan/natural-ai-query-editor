package com.example.mcpsql.client.service;

import com.example.mcpsql.client.config.PreviewProperties;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Service;

/**
 * Ties a confirmation to the exact SQL a user previewed. A token is "issuedAtMillis.signature", valid for
 * one use, so a double click or a replayed request cannot apply the same write twice.
 */
@Service
public class PreviewTokenService {

    private static final String ALGORITHM = "HmacSHA256";
    private static final char SEPARATOR = '.';
    private static final char FIELD_END = '\0';

    private final SecretKeySpec key;
    private final Duration ttl;
    private final Clock clock;
    private final Map<String, Instant> usedUntil = new ConcurrentHashMap<>();

    public PreviewTokenService(PreviewProperties properties, Clock clock) {
        this.key = new SecretKeySpec(properties.secret().getBytes(StandardCharsets.UTF_8), ALGORITHM);
        this.ttl = properties.ttl();
        this.clock = clock;
    }

    public String issue(String sql, String user) {
        long issuedAt = clock.millis();
        return issuedAt + "" + SEPARATOR + sign(sql, user, issuedAt);
    }

    /** @throws PreviewTokenException unless the token is genuine, unexpired, unused and for this SQL and user */
    public void consume(String token, String sql, String user) {
        int separator = token.indexOf(SEPARATOR);
        if (separator < 0) {
            throw new PreviewTokenException();
        }
        long issuedAt = parseMillis(token.substring(0, separator));
        byte[] expected = sign(sql, user, issuedAt).getBytes(StandardCharsets.UTF_8);
        byte[] actual = token.substring(separator + 1).getBytes(StandardCharsets.UTF_8);
        Instant now = clock.instant();
        Instant issued = Instant.ofEpochMilli(issuedAt);
        if (!MessageDigest.isEqual(expected, actual) || issued.isAfter(now) || now.isAfter(issued.plus(ttl))) {
            throw new PreviewTokenException();
        }
        usedUntil.values().removeIf(now::isAfter);
        if (usedUntil.putIfAbsent(token, issued.plus(ttl)) != null) {
            throw new PreviewTokenException();
        }
    }

    private static long parseMillis(String text) {
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException e) {
            throw new PreviewTokenException();
        }
    }

    private String sign(String sql, String user, long issuedAt) {
        String payload = normalize(sql) + FIELD_END + user + FIELD_END + issuedAt;
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(key);
            byte[] signature = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(signature);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC-SHA256 is unavailable", e);
        }
    }

    private static String normalize(String sql) {
        return sql.strip();
    }
}
