package com.example.mcpsql.server.audit;

import com.example.mcpsql.server.exec.QueryExecutionException;
import com.example.mcpsql.server.guard.QueryRejectedException;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.mcp.McpToolUtils;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/** Writes one JSON line per tool call. It records what was asked and how it ended, never the rows themselves. */
@Component
public class AuditLogger {

    public static final String LOGGER_NAME = "mcp.audit";

    private static final Logger LOG = LoggerFactory.getLogger(LOGGER_NAME);
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final int MAX_LOGGED_SQL = 2_000;
    private static final String UNKNOWN_CALLER = "unknown";
    private static final String ALLOWED = "ALLOWED";
    private static final String REJECTED = "REJECTED";
    private static final String TOOL_ERROR = "TOOL_ERROR";

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private record Entry(
            String timestamp,
            String caller,
            String tool,
            String sql,
            String decision,
            String ruleId,
            Boolean dryRun,
            long durationMs,
            Integer rows,
            String errorCode) {
    }

    /**
     * Runs {@code work} and logs the outcome, whether it returns or throws.
     *
     * @param sql    null for tools that take no SQL
     * @param dryRun null for tools that do not write
     */
    public <T> T audited(ToolContext context, String tool, String sql, Boolean dryRun,
            Supplier<T> work, ToIntFunction<T> rowCount) {
        String caller = callerOf(context);
        String logged = sql == null || sql.length() <= MAX_LOGGED_SQL ? sql : sql.substring(0, MAX_LOGGED_SQL);
        long start = System.nanoTime();
        String decision = ALLOWED;
        String ruleId = null;
        String errorCode = null;
        Integer rows = null;
        try {
            T result = work.get();
            rows = rowCount.applyAsInt(result);
            return result;
        } catch (QueryRejectedException e) {
            decision = REJECTED;
            ruleId = e.ruleId();
            throw e;
        } catch (QueryExecutionException e) {
            errorCode = e.code();
            throw e;
        } catch (RuntimeException e) {
            errorCode = TOOL_ERROR;
            throw e;
        } finally {
            long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
            LOG.info(JSON.writeValueAsString(
                    new Entry(Instant.now().toString(), caller, tool, logged, decision, ruleId, dryRun, durationMs, rows, errorCode)));
        }
    }

    /** The name the MCP client declared when it connected. All clients share one API key. */
    private static String callerOf(ToolContext context) {
        return McpToolUtils.getMcpExchange(context)
                .map(exchange -> exchange.getClientInfo())
                .map(client -> client.name())
                .orElse(UNKNOWN_CALLER);
    }
}
