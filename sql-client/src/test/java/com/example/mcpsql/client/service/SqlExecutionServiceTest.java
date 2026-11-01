package com.example.mcpsql.client.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.mcpsql.client.dto.SelectResult;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SqlExecutionServiceTest {

    @Test
    void sendsTheExactSqlToRunSelect() {
        McpToolInvoker tools = mock(McpToolInvoker.class);
        SelectResult expected = new SelectResult(List.of("n"), List.of(List.of(1)), 1, false, 2);
        when(tools.call("run_select", Map.of("sql", "SELECT 1 AS n"), SelectResult.class)).thenReturn(expected);

        assertThat(new SqlExecutionService(tools).select("SELECT 1 AS n")).isEqualTo(expected);
    }
}
