package com.example.mcpsql.client.service;

import com.example.mcpsql.client.dto.ExecuteResponse;
import com.example.mcpsql.client.dto.SelectResult;
import com.example.mcpsql.client.dto.StatementKind;
import com.example.mcpsql.client.dto.TableInfo;
import com.example.mcpsql.client.dto.WriteResult;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/** Runs SQL through the MCP server, which alone decides whether it is allowed. */
@Service
public class SqlExecutionService {

    private final McpToolInvoker tools;
    private final PreviewTokenService previewTokens;

    public SqlExecutionService(McpToolInvoker tools, PreviewTokenService previewTokens) {
        this.tools = tools;
        this.previewTokens = previewTokens;
    }

    /**
     * Runs a SELECT, or dry-runs a write and returns a token for confirming it. Anything else, such as a
     * DELETE, goes to run_select so that the server's rejection reaches the user.
     */
    public ExecuteResponse execute(String sql, String user) {
        StatementKind kind = SqlClassifier.classify(sql).orElse(StatementKind.SELECT);
        if (kind == StatementKind.SELECT) {
            return ExecuteResponse.ofSelect(tools.call("run_select", Map.of("sql", sql), SelectResult.class));
        }
        WriteResult preview = write(kind, sql, true);
        return ExecuteResponse.ofPreview(kind, preview, previewTokens.issue(sql, user));
    }

    /** Commits exactly the SQL that was previewed. */
    public WriteResult confirm(String sql, String previewToken, String user) {
        previewTokens.consume(previewToken, sql, user);
        StatementKind kind = SqlClassifier.classify(sql).orElseThrow(PreviewTokenException::new);
        return write(kind, sql, false);
    }

    public List<TableInfo> tables() {
        return Arrays.asList(tools.call("list_tables", Map.of(), TableInfo[].class));
    }

    private WriteResult write(StatementKind kind, String sql, boolean dryRun) {
        String tool = kind == StatementKind.INSERT ? "run_insert" : "run_update";
        return tools.call(tool, Map.of("sql", sql, "dryRun", dryRun), WriteResult.class);
    }
}
