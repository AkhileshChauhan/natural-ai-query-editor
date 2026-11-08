package com.example.mcpsql.client.service;

import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Calls tools on the MCP server and parses their JSON results. The connection is made on first use and
 * replaced if it has died, so the page works while the server is down and recovers when it returns.
 */
@Component
public class McpToolInvoker {

    private final Supplier<McpSyncClient> connections;
    private final JsonMapper json;
    private McpSyncClient client;

    public McpToolInvoker(Supplier<McpSyncClient> connections, JsonMapper json) {
        this.connections = connections;
        this.json = json;
    }

    /** Calls a tool that returns JSON. */
    public <T> T call(String tool, Map<String, Object> arguments, Class<T> resultType) {
        try {
            return json.readValue(text(tool, arguments), resultType);
        } catch (JacksonException e) {
            throw new McpToolException("MCP_BAD_RESPONSE: The MCP server returned an unreadable result", e);
        }
    }

    /** Calls a tool and returns its text as it is. */
    public String text(String tool, Map<String, Object> arguments) {
        McpSchema.CallToolResult result = send(tool, arguments);
        String text = textOf(result);
        if (Boolean.TRUE.equals(result.isError())) {
            throw new McpToolException(text);
        }
        return text;
    }

    private McpSchema.CallToolResult send(String tool, Map<String, Object> arguments) {
        try {
            return connected().callTool(new McpSchema.CallToolRequest(tool, arguments));
        } catch (RuntimeException e) {
            throw new McpToolException("MCP_UNAVAILABLE: The MCP server could not be reached", e);
        }
    }

    /**
     * Checks the connection before the call rather than retrying after it fails: a failed call may already
     * have reached the database, and sending a write twice could apply it twice.
     */
    private synchronized McpSyncClient connected() {
        if (client != null) {
            try {
                client.ping();
                return client;
            } catch (RuntimeException e) {
                client.close();
                client = null;
            }
        }
        McpSyncClient fresh = connections.get();
        try {
            fresh.initialize();
        } catch (RuntimeException e) {
            fresh.close();
            throw e;
        }
        client = fresh;
        return fresh;
    }

    private static String textOf(McpSchema.CallToolResult result) {
        return result.content().stream()
                .filter(McpSchema.TextContent.class::isInstance)
                .map(content -> ((McpSchema.TextContent) content).text())
                .collect(Collectors.joining());
    }
}
