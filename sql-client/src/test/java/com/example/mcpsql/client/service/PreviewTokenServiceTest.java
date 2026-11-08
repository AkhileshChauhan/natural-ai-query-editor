package com.example.mcpsql.client.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.mcpsql.client.config.PreviewProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PreviewTokenServiceTest {

    private static final String SQL = "UPDATE shop.products SET stock_qty = 1 WHERE product_id = 1";
    private static final String USER = "alice";
    private static final String SECRET = "a-secret-that-is-at-least-32-characters";
    private static final Duration TTL = Duration.ofMinutes(5);

    private static final class MovableClock extends Clock {
        private Instant now = Instant.parse("2026-01-01T00:00:00Z");

        void advance(Duration by) {
            now = now.plus(by);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }

    private final MovableClock clock = new MovableClock();
    private final PreviewTokenService tokens = new PreviewTokenService(new PreviewProperties(SECRET, TTL), clock);

    private void assertInvalid(String token, String sql, String user) {
        assertThatThrownBy(() -> tokens.consume(token, sql, user)).isInstanceOf(PreviewTokenException.class);
    }

    @Test
    void acceptsTheTokenForTheSameSqlAndUser() {
        assertThatCode(() -> tokens.consume(tokens.issue(SQL, USER), SQL, USER)).doesNotThrowAnyException();
    }

    @Test
    void ignoresSurroundingWhitespace() {
        assertThatCode(() -> tokens.consume(tokens.issue(SQL, USER), "  " + SQL + "\n", USER)).doesNotThrowAnyException();
    }

    @Test
    void refusesDifferentSql() {
        assertInvalid(tokens.issue(SQL, USER), SQL + " OR 1 = 1", USER);
    }

    @Test
    void refusesAnotherUser() {
        assertInvalid(tokens.issue(SQL, USER), SQL, "mallory");
    }

    @Test
    void refusesATamperedSignature() {
        String token = tokens.issue(SQL, USER);
        char last = token.charAt(token.length() - 1);

        assertInvalid(token.substring(0, token.length() - 1) + (last == 'A' ? 'B' : 'A'), SQL, USER);
    }

    @Test
    void refusesATamperedIssueTime() {
        String token = tokens.issue(SQL, USER);
        long later = clock.millis() + 1_000;

        assertInvalid(later + token.substring(token.indexOf('.')), SQL, USER);
    }

    @Test
    void refusesATokenSignedWithAnotherSecret() {
        var other = new PreviewTokenService(new PreviewProperties(SECRET + "-other", TTL), clock);

        assertInvalid(other.issue(SQL, USER), SQL, USER);
    }

    @Test
    void acceptsATokenAtTheEndOfItsLifetime() {
        String token = tokens.issue(SQL, USER);
        clock.advance(TTL);

        assertThatCode(() -> tokens.consume(token, SQL, USER)).doesNotThrowAnyException();
    }

    @Test
    void refusesAnExpiredToken() {
        String token = tokens.issue(SQL, USER);
        clock.advance(TTL.plusMillis(1));

        assertInvalid(token, SQL, USER);
    }

    @Test
    void refusesASecondUse() {
        String token = tokens.issue(SQL, USER);
        tokens.consume(token, SQL, USER);

        assertInvalid(token, SQL, USER);
    }

    @Test
    void aFailedAttemptDoesNotUseUpTheToken() {
        String token = tokens.issue(SQL, USER);
        assertInvalid(token, SQL, "mallory");

        assertThatCode(() -> tokens.consume(token, SQL, USER)).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "no-separator", ".", "abc.def", "123.", "99999999999999999999.x"})
    void refusesMalformedTokens(String token) {
        assertInvalid(token, SQL, USER);
    }
}
