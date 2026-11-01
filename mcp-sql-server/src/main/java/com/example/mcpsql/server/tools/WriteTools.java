package com.example.mcpsql.server.tools;

import com.example.mcpsql.server.dto.WriteResult;
import com.example.mcpsql.server.exec.WriteExecutor;
import com.example.mcpsql.server.guard.StatementKind;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Absent when {@code sqlguard.read-only} is true, so the server then offers no write tools at all. */
@Component
@ConditionalOnProperty(name = "sqlguard.read-only", havingValue = "false", matchIfMissing = true)
public class WriteTools {

    private static final String DRY_RUN_DESCRIPTION =
            "true previews the affected row count and rolls back; false commits";

    private final GuardedSql guardedSql;
    private final WriteExecutor executor;

    WriteTools(GuardedSql guardedSql, WriteExecutor executor) {
        this.guardedSql = guardedSql;
        this.executor = executor;
    }

    @Tool(name = "run_insert", description = """
            Run one PostgreSQL INSERT into an allowed table, with optional RETURNING or ON CONFLICT DO NOTHING. \
            Use dryRun=true to preview the affected row count. DELETE, TRUNCATE, DDL and multiple statements \
            are rejected.""")
    public WriteResult runInsert(
            @ToolParam(description = "A single PostgreSQL INSERT statement") String sql,
            @ToolParam(description = DRY_RUN_DESCRIPTION) Boolean dryRun,
            ToolContext context) {
        return write(context, "run_insert", sql, StatementKind.INSERT, dryRun);
    }

    @Tool(name = "run_update", description = """
            Run one PostgreSQL UPDATE on any table, with optional FROM, WHERE and RETURNING. There is no limit \
            on affected rows, so use dryRun=true to preview the count first. DELETE, TRUNCATE, DDL and multiple \
            statements are rejected.""")
    public WriteResult runUpdate(
            @ToolParam(description = "A single PostgreSQL UPDATE statement") String sql,
            @ToolParam(description = DRY_RUN_DESCRIPTION) Boolean dryRun,
            ToolContext context) {
        return write(context, "run_update", sql, StatementKind.UPDATE, dryRun);
    }

    /** MCP schema validation already refuses a missing dryRun; only an explicit false may ever commit. */
    private WriteResult write(ToolContext context, String tool, String sql, StatementKind kind, Boolean dryRun) {
        boolean preview = !Boolean.FALSE.equals(dryRun);
        return guardedSql.run(context, tool, sql, kind, preview,
                allowed -> executor.execute(allowed, preview), WriteResult::affectedRows);
    }
}
