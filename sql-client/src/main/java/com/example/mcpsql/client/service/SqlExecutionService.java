package com.example.mcpsql.client.service;

import com.example.mcpsql.client.dto.SelectResult;
import java.util.Map;
import org.springframework.stereotype.Service;

/** Runs SQL through the MCP server, which alone decides whether it is allowed. */
@Service
public class SqlExecutionService {

    private final McpToolInvoker tools;

    public SqlExecutionService(McpToolInvoker tools) {
        this.tools = tools;
    }

    public SelectResult select(String sql) {
        return tools.call("run_select", Map.of("sql", sql), SelectResult.class);
    }
}
