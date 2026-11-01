package com.example.mcpsql.server.tools;

import com.example.mcpsql.server.dto.QueryResult;
import com.example.mcpsql.server.exec.QueryExecutor;
import com.example.mcpsql.server.guard.StatementKind;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Component
public class ReadTools {

    private final GuardedSql guardedSql;
    private final QueryExecutor executor;

    ReadTools(GuardedSql guardedSql, QueryExecutor executor) {
        this.guardedSql = guardedSql;
        this.executor = executor;
    }

    @Tool(name = "run_select", description = """
            Run one PostgreSQL SELECT in a read-only transaction and return columns and rows. Joins, CTEs, \
            window functions and subqueries are supported. Results are capped at the server's row limit, and \
            truncated=true means more rows exist. Statements time out after the server's query timeout. \
            DELETE, TRUNCATE, DDL and multiple statements are rejected.""")
    public QueryResult runSelect(
            @ToolParam(description = "A single PostgreSQL SELECT statement") String sql,
            @ToolParam(description = "Optional lower row limit for this call", required = false) Integer maxRows,
            ToolContext context) {
        return guardedSql.run(context, "run_select", sql, StatementKind.SELECT, null,
                allowed -> executor.select(allowed, maxRows), QueryResult::rowCount);
    }
}
