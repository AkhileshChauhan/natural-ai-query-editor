package com.example.mcpsql.server.tools;

import static org.assertj.core.api.Assertions.assertThat;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.spec.McpSchema;
import java.net.http.HttpRequest;
import java.util.List;
import java.util.Map;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** A real MCP session, as MCP Inspector and sql-client open one. */
final class McpTestClient implements AutoCloseable {

    static final String API_KEY_HEADER = "X-API-Key";
    static final String API_KEY = "test-api-key";
    static final String CLIENT_NAME = "test-client";

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final McpSyncClient client;

    McpTestClient(int port) {
        HttpRequest.Builder request = HttpRequest.newBuilder().header(API_KEY_HEADER, API_KEY);
        client = McpClient.sync(HttpClientSseClientTransport.builder("http://localhost:" + port)
                        .requestBuilder(request)
                        .build())
                .clientInfo(new McpSchema.Implementation(CLIENT_NAME, "1.0"))
                .build();
        client.initialize();
    }

    List<McpSchema.Tool> tools() {
        return client.listTools().tools();
    }

    McpSchema.CallToolResult call(String tool, Map<String, Object> arguments) {
        return client.callTool(new McpSchema.CallToolRequest(tool, arguments));
    }

    /** Parses the JSON result of a call that must succeed. */
    JsonNode ok(String tool, Map<String, Object> arguments) {
        McpSchema.CallToolResult result = call(tool, arguments);
        assertThat(result.isError()).as(text(result)).isFalse();
        return JSON.readTree(text(result));
    }

    /** Returns the error text of a call that must fail. */
    String error(String tool, Map<String, Object> arguments) {
        McpSchema.CallToolResult result = call(tool, arguments);
        assertThat(result.isError()).as(text(result)).isTrue();
        return text(result);
    }

    static String text(McpSchema.CallToolResult result) {
        return ((McpSchema.TextContent) result.content().getFirst()).text();
    }

    @Override
    public void close() {
        client.closeGracefully();
    }
}
