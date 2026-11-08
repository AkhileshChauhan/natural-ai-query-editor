package com.example.mcpsql.client.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.mcpsql.client.dto.ExecuteResponse;
import com.example.mcpsql.client.dto.SelectResult;
import com.example.mcpsql.client.dto.StatementKind;
import com.example.mcpsql.client.dto.WriteResult;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SqlExecutionServiceTest {

    private static final String UPDATE = "UPDATE shop.products SET stock_qty = 1 WHERE product_id = 1";
    private static final String INSERT = "INSERT INTO shop.orders (customer_id) VALUES (1)";
    private static final String USER = "alice";
    private static final WriteResult PREVIEW = new WriteResult(3, false, true, null);
    private static final WriteResult COMMITTED = new WriteResult(3, true, false, null);

    private final McpToolInvoker tools = mock(McpToolInvoker.class);
    private final PreviewTokenService tokens = mock(PreviewTokenService.class);
    private final SqlExecutionService service = new SqlExecutionService(tools, tokens);

    @Test
    void runsASelectWithTheExactSql() {
        SelectResult expected = new SelectResult(List.of("n"), List.of(List.of(1)), 1, false, 2);
        when(tools.call("run_select", Map.of("sql", "SELECT 1 AS n"), SelectResult.class)).thenReturn(expected);

        ExecuteResponse response = service.execute("SELECT 1 AS n", USER);

        assertThat(response.kind()).isEqualTo(StatementKind.SELECT);
        assertThat(response.select()).isEqualTo(expected);
        assertThat(response.previewToken()).isNull();
    }

    @Test
    void previewsAnUpdateAsADryRunAndIssuesAToken() {
        when(tools.call("run_update", Map.of("sql", UPDATE, "dryRun", true), WriteResult.class)).thenReturn(PREVIEW);
        when(tokens.issue(UPDATE, USER)).thenReturn("token");

        ExecuteResponse response = service.execute(UPDATE, USER);

        assertThat(response.kind()).isEqualTo(StatementKind.UPDATE);
        assertThat(response.write()).isEqualTo(PREVIEW);
        assertThat(response.previewToken()).isEqualTo("token");
    }

    @Test
    void sendsInsertsToTheInsertTool() {
        when(tools.call("run_insert", Map.of("sql", INSERT, "dryRun", true), WriteResult.class)).thenReturn(PREVIEW);

        assertThat(service.execute(INSERT, USER).kind()).isEqualTo(StatementKind.INSERT);
    }

    @Test
    void sendsUnrecognisedSqlToRunSelectSoTheServerRejectsIt() {
        when(tools.call(eq("run_select"), any(), eq(SelectResult.class)))
                .thenThrow(new McpToolException("REJECTED[STATEMENT_NOT_ALLOWED]: DELETE statements are not permitted"));

        assertThatThrownBy(() -> service.execute("DELETE FROM shop.orders", USER))
                .hasMessageContaining("DELETE statements are not permitted");
        verify(tokens, never()).issue(anyString(), anyString());
    }

    @Test
    void confirmCommitsOnlyAfterTheTokenIsAccepted() {
        when(tools.call("run_update", Map.of("sql", UPDATE, "dryRun", false), WriteResult.class)).thenReturn(COMMITTED);

        assertThat(service.confirm(UPDATE, "token", USER)).isEqualTo(COMMITTED);
        verify(tokens).consume("token", UPDATE, USER);
    }

    @Test
    void confirmNeverCommitsWithARefusedToken() {
        org.mockito.Mockito.doThrow(new PreviewTokenException()).when(tokens).consume("bad", UPDATE, USER);

        assertThatThrownBy(() -> service.confirm(UPDATE, "bad", USER)).isInstanceOf(PreviewTokenException.class);
        verify(tools, never()).call(anyString(), any(), any());
    }
}
