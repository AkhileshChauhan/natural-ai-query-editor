package com.example.mcpsql.client.service;

import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/** Calls tools on the {@code sql} MCP connection and parses their JSON results. */
@Component
public class McpToolInvoker {

    /** Spring AI names each client "{spring.ai.mcp.client.name} - {connection}". */
    private static final String CONNECTION_SUFFIX = " - sql";

    private final McpSyncClient client;
    private final JsonMapper json;

    public McpToolInvoker(List<McpSyncClient> clients, JsonMapper json) {
        this.client = clients.stream()
                .filter(candidate -> candidate.getClientInfo().name().endsWith(CONNECTION_SUFFIX))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No MCP connection named 'sql' is configured"));
        this.json = json;
    }

    public <T> T call(String tool, Map<String, Object> arguments, Class<T> resultType) {
        McpSchema.CallToolResult result = send(tool, arguments);
        String text = textOf(result);
        if (Boolean.TRUE.equals(result.isError())) {
            throw new McpToolException(text);
        }
        try {
            return json.readValue(text, resultType);
        } catch (JacksonException e) {
            throw new McpToolException("MCP_BAD_RESPONSE: The MCP server returned an unreadable result", e);
        }
    }

    private McpSchema.CallToolResult send(String tool, Map<String, Object> arguments) {
        try {
            return client.callTool(new McpSchema.CallToolRequest(tool, arguments));
        } catch (RuntimeException e) {
            throw new McpToolException("MCP_UNAVAILABLE: The MCP server could not be reached", e);
        }
    }

    private static String textOf(McpSchema.CallToolResult result) {
        return result.content().stream()
                .filter(McpSchema.TextContent.class::isInstance)
                .map(content -> ((McpSchema.TextContent) content).text())
                .collect(Collectors.joining());
    }
}
