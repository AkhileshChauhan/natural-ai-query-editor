package com.example.mcpsql.client.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.mcpsql.client.dto.SelectResult;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.json.JsonMapper;

class McpToolInvokerTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String RESULT = """
            {"columns":["id","name"],"rows":[[1,"a"],[2,null]],"rowCount":2,"truncated":true,"elapsedMs":5}
            """;

    private final McpSyncClient client = mock(McpSyncClient.class);
    private McpToolInvoker invoker;

    private void toolReturns(boolean isError, String text) {
        toolReturns(client, isError, text);
    }

    private static void toolReturns(McpSyncClient target, boolean isError, String text) {
        when(target.callTool(any())).thenReturn(McpSchema.CallToolResult.builder()
                .content(List.of(new McpSchema.TextContent(text)))
                .isError(isError)
                .build());
    }

    private void assertCallFails(String message) {
        assertThatThrownBy(() -> invoker.call("run_select", Map.of("sql", "SELECT 1"), SelectResult.class))
                .isInstanceOf(McpToolException.class)
                .hasMessage(message);
    }

    @BeforeEach
    void setUp() {
        invoker = new McpToolInvoker(() -> client, JSON);
    }

    @Test
    void sendsTheToolNameAndArguments() {
        toolReturns(false, RESULT);

        invoker.call("run_select", Map.of("sql", "SELECT 1"), SelectResult.class);

        ArgumentCaptor<McpSchema.CallToolRequest> request = ArgumentCaptor.forClass(McpSchema.CallToolRequest.class);
        verify(client).callTool(request.capture());
        assertThat(request.getValue().name()).isEqualTo("run_select");
        assertThat(request.getValue().arguments()).containsEntry("sql", "SELECT 1");
    }

    @Test
    void parsesTheJsonResult() {
        toolReturns(false, RESULT);

        SelectResult result = invoker.call("run_select", Map.of("sql", "SELECT 1"), SelectResult.class);

        assertThat(result.columns()).containsExactly("id", "name");
        assertThat(result.rows().get(1)).containsExactly(2, null);
        assertThat(result.rowCount()).isEqualTo(2);
        assertThat(result.truncated()).isTrue();
        assertThat(result.elapsedMs()).isEqualTo(5);
    }

    @Test
    void toleratesFieldsAddedByNewerServers() {
        toolReturns(false, RESULT.strip().replaceFirst("}$", ",\"newField\":1}"));

        assertThat(invoker.call("run_select", Map.of("sql", "SELECT 1"), SelectResult.class).rowCount()).isEqualTo(2);
    }

    @Test
    void passesTheServerRejectionThrough() {
        toolReturns(true, "REJECTED[STATEMENT_NOT_ALLOWED]: DELETE statements are not permitted");

        assertCallFails("REJECTED[STATEMENT_NOT_ALLOWED]: DELETE statements are not permitted");
    }

    @Test
    void reportsUnreadableResultsWithoutEchoingThem() {
        toolReturns(false, "not json");

        assertCallFails("MCP_BAD_RESPONSE: The MCP server returned an unreadable result");
    }

    @Test
    void hidesTransportFailureDetails() {
        when(client.callTool(any())).thenThrow(new IllegalStateException("connection to internal-host:8081 refused"));

        assertCallFails("MCP_UNAVAILABLE: The MCP server could not be reached");
    }

    @Test
    void connectsOnFirstUse() {
        toolReturns(false, RESULT);

        invoker.call("run_select", Map.of("sql", "SELECT 1"), SelectResult.class);

        verify(client).initialize();
    }

    @Test
    void reportsAFailedConnectionTheSameWay() {
        when(client.initialize()).thenThrow(new IllegalStateException("refused"));

        assertCallFails("MCP_UNAVAILABLE: The MCP server could not be reached");
    }

    @Test
    void returnsPlainTextAsItIs() {
        toolReturns(false, "shop.orders(order_id integer PK)");

        assertThat(invoker.text("schema_overview", Map.of())).isEqualTo("shop.orders(order_id integer PK)");
    }

    @Test
    void keepsAHealthyConnection() {
        toolReturns(false, RESULT);

        invoker.call("run_select", Map.of("sql", "SELECT 1"), SelectResult.class);
        invoker.call("run_select", Map.of("sql", "SELECT 2"), SelectResult.class);

        verify(client, times(1)).initialize();
        verify(client).ping();
    }

    @Test
    void replacesADeadConnectionBeforeCalling() {
        McpSyncClient replacement = mock(McpSyncClient.class);
        Iterator<McpSyncClient> connections = List.of(client, replacement).iterator();
        invoker = new McpToolInvoker(connections::next, JSON);
        toolReturns(false, RESULT);
        toolReturns(replacement, false, RESULT);
        invoker.call("run_select", Map.of("sql", "SELECT 1"), SelectResult.class);
        when(client.ping()).thenThrow(new IllegalStateException("gone"));

        invoker.call("run_select", Map.of("sql", "SELECT 2"), SelectResult.class);

        verify(client).close();
        verify(replacement).initialize();
        verify(replacement).callTool(any());
    }

    @Test
    void neverResendsAFailedCall() {
        when(client.callTool(any())).thenThrow(new IllegalStateException("broken mid-call"));

        assertCallFails("MCP_UNAVAILABLE: The MCP server could not be reached");

        verify(client, times(1)).callTool(any());
    }

    @Test
    void closesAConnectionThatFailedToStartAndTriesAgainNextTime() {
        when(client.initialize()).thenThrow(new IllegalStateException("refused")).thenReturn(null);
        toolReturns(false, RESULT);

        assertCallFails("MCP_UNAVAILABLE: The MCP server could not be reached");
        invoker.call("run_select", Map.of("sql", "SELECT 1"), SelectResult.class);

        verify(client).close();
        verify(client, times(2)).initialize();
    }
}
