package com.example.mcpsql.client.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.mcpsql.client.dto.SelectResult;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
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

    private final McpSyncClient client = namedClient("sql-client - sql");
    private McpToolInvoker invoker;

    private static McpSyncClient namedClient(String name) {
        McpSyncClient client = mock(McpSyncClient.class);
        when(client.getClientInfo()).thenReturn(new McpSchema.Implementation(name, "1.0"));
        return client;
    }

    private void toolReturns(boolean isError, String text) {
        when(client.callTool(any())).thenReturn(McpSchema.CallToolResult.builder()
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
        invoker = new McpToolInvoker(List.of(client), JSON);
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
    void picksTheSqlConnectionFromSeveral() {
        McpSyncClient other = namedClient("sql-client - other");
        invoker = new McpToolInvoker(List.of(other, client), JSON);
        toolReturns(false, RESULT);

        invoker.call("run_select", Map.of("sql", "SELECT 1"), SelectResult.class);

        verify(client).callTool(any());
    }

    @Test
    void refusesToStartWithoutTheSqlConnection() {
        McpSyncClient other = namedClient("sql-client - other");

        assertThatThrownBy(() -> new McpToolInvoker(List.of(other), JSON))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("'sql'");
    }
}
