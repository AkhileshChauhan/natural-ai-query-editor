package com.example.mcpsql.server.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.example.mcpsql.server.guard.QueryRejectedException;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;

class AuditLoggerTest {

    private static final ToolContext NO_SESSION = new ToolContext(Map.of());

    private final AuditLogger audit = new AuditLogger();
    private final Logger logger = (Logger) LoggerFactory.getLogger(AuditLogger.LOGGER_NAME);
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    @BeforeEach
    void capture() {
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void release() {
        logger.detachAppender(appender);
    }

    private String line() {
        assertThat(appender.list).hasSize(1);
        return appender.list.getFirst().getFormattedMessage();
    }

    @Test
    void logsOneLineForASuccess() {
        String result = audit.audited(NO_SESSION, "run_select", "SELECT 1", null, () -> "rows", text -> 4);

        assertThat(result).isEqualTo("rows");
        assertThat(line()).startsWith("{\"timestamp\":\"20").endsWith("}").contains(
                "\"caller\":\"unknown\"", "\"tool\":\"run_select\"", "\"sql\":\"SELECT 1\"",
                "\"decision\":\"ALLOWED\"", "\"rows\":4");
        assertThat(line()).doesNotContain("ruleId", "errorCode", "dryRun");
    }

    @Test
    void rethrowsAndLogsARejection() {
        assertThatThrownBy(() -> audit.audited(NO_SESSION, "run_update", "DROP TABLE x", true, () -> {
            throw new QueryRejectedException("RULE", "why");
        }, value -> 0)).isInstanceOf(QueryRejectedException.class);

        assertThat(line()).contains("\"decision\":\"REJECTED\"", "\"ruleId\":\"RULE\"", "\"dryRun\":true");
    }

    @Test
    void rethrowsAndLogsAnUnexpectedFailure() {
        assertThatThrownBy(() -> audit.audited(NO_SESSION, "describe_table", null, null, () -> {
            throw new IllegalArgumentException("Unknown table: x");
        }, value -> 0)).isInstanceOf(IllegalArgumentException.class);

        assertThat(line()).contains("\"errorCode\":\"TOOL_ERROR\"").doesNotContain("Unknown table");
    }

    @Test
    void keepsMultiLineSqlOnOneLogLine() {
        audit.audited(NO_SESSION, "run_select", "SELECT 1\nFROM t", null, () -> "", text -> 0);

        assertThat(line()).doesNotContain("\n").contains("SELECT 1\\nFROM t");
    }

    @Test
    void shortensVeryLongSql() {
        audit.audited(NO_SESSION, "run_select", "x".repeat(5_000), null, () -> "", text -> 0);

        assertThat(line()).contains("x".repeat(2_000)).doesNotContain("x".repeat(2_001));
    }
}
