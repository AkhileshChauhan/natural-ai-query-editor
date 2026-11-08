package com.example.mcpsql.client.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.mcpsql.client.dto.SelectResult;
import com.example.mcpsql.client.dto.TableInfo;
import com.example.mcpsql.client.dto.WriteResult;
import com.example.mcpsql.client.service.McpToolException;
import com.example.mcpsql.client.service.McpToolInvoker;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
class QueryApiTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String UPDATE = "UPDATE shop.products SET stock_qty = 1 WHERE product_id = 1";

    @Autowired
    WebApplicationContext context;

    @MockitoBean
    McpToolInvoker tools;

    @MockitoBean
    ChatModel chatModel;

    MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        when(chatModel.getOptions()).thenReturn(ChatOptions.builder().build());
    }

    private static String body(Map<String, String> fields) {
        return JSON.writeValueAsString(fields);
    }

    private ResultActions postAs(String username, String path, Map<String, String> fields) throws Exception {
        return mvc.perform(post(path).with(user(username)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body(fields)));
    }

    private ResultActions execute(String sql) throws Exception {
        return postAs("alice", "/api/sql/execute", Map.of("sql", sql));
    }

    private String previewToken() throws Exception {
        when(tools.call("run_update", Map.of("sql", UPDATE, "dryRun", true), WriteResult.class))
                .thenReturn(new WriteResult(1, false, true, null));
        String response = execute(UPDATE).andReturn().getResponse().getContentAsString();
        return JSON.readTree(response).get("previewToken").asString();
    }

    @Test
    void redirectsThePageToLoginWhenSignedOut() throws Exception {
        mvc.perform(get("/").accept(MediaType.TEXT_HTML)).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/login"));
    }

    @Test
    void answersApiCallsWith401WhenSignedOut() throws Exception {
        mvc.perform(get("/api/schema/tables")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/sql/execute").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("sql", "SELECT 1")))).andExpect(status().isUnauthorized());
    }

    @Test
    void refusesPostsWithoutACsrfToken() throws Exception {
        mvc.perform(post("/api/sql/execute").with(user("alice")).contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("sql", "SELECT 1")))).andExpect(status().isForbidden());
        verify(tools, never()).call(anyString(), anyMap(), any());
    }

    @Test
    void servesThePageWithItsCsrfMetaTags() throws Exception {
        mvc.perform(get("/").with(user("alice")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("name=\"csrf-token\" content=\"")))
                .andExpect(content().string(containsString("name=\"csrf-header\" content=\"X-CSRF-TOKEN\"")))
                .andExpect(content().string(containsString("alice")));
    }

    @Test
    void neverWritesCellsAsHtml() throws Exception {
        mvc.perform(get("/app.js").with(user("alice")))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("innerHTML"))))
                .andExpect(content().string(not(containsString("insertAdjacentHTML"))));
    }

    @Test
    void generatesSql() throws Exception {
        when(tools.text("schema_overview", Map.of())).thenReturn("shop.orders(order_id integer PK)");
        when(chatModel.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("""
                {"sql":"SELECT order_id FROM shop.orders","kind":"SELECT","explanation":"Lists orders","tablesUsed":["shop.orders"]}
                """)))));

        postAs("alice", "/api/sql/generate", Map.of("question", "list orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sql").value("SELECT order_id FROM shop.orders"))
                .andExpect(jsonPath("$.kind").value("SELECT"))
                .andExpect(jsonPath("$.tablesUsed[0]").value("shop.orders"));
    }

    @Test
    void reportsFailedGeneration() throws Exception {
        when(tools.text("schema_overview", Map.of())).thenReturn("schema");
        when(chatModel.call(any(Prompt.class))).thenThrow(new IllegalStateException("provider detail"));

        postAs("alice", "/api/sql/generate", Map.of("question", "list orders"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("GENERATION_FAILED"))
                .andExpect(content().string(not(containsString("provider detail"))));
    }

    @Test
    void runsASelect() throws Exception {
        when(tools.call("run_select", Map.of("sql", "SELECT 1 AS n"), SelectResult.class))
                .thenReturn(new SelectResult(List.of("n"), List.of(List.of(1)), 1, false, 3));

        execute("SELECT 1 AS n")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kind").value("SELECT"))
                .andExpect(jsonPath("$.select.rows[0][0]").value(1))
                .andExpect(jsonPath("$.previewToken").isEmpty());
    }

    @Test
    void previewsThenConfirmsTheSameSql() throws Exception {
        String token = previewToken();
        when(tools.call("run_update", Map.of("sql", UPDATE, "dryRun", false), WriteResult.class))
                .thenReturn(new WriteResult(1, true, false, null));

        postAs("alice", "/api/sql/confirm", Map.of("sql", UPDATE, "previewToken", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.committed").value(true))
                .andExpect(jsonPath("$.affectedRows").value(1));
    }

    @Test
    void refusesToConfirmEditedSql() throws Exception {
        String token = previewToken();

        postAs("alice", "/api/sql/confirm", Map.of("sql", UPDATE + " OR 1 = 1", "previewToken", token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PREVIEW_INVALID"));
        verify(tools, never()).call(eq("run_update"), eq(Map.of("sql", UPDATE + " OR 1 = 1", "dryRun", false)), any());
    }

    @Test
    void refusesToConfirmAnotherUsersPreview() throws Exception {
        String token = previewToken();

        postAs("mallory", "/api/sql/confirm", Map.of("sql", UPDATE, "previewToken", token))
                .andExpect(status().isConflict());
    }

    @Test
    void refusesToConfirmTheSamePreviewTwice() throws Exception {
        String token = previewToken();
        when(tools.call("run_update", Map.of("sql", UPDATE, "dryRun", false), WriteResult.class))
                .thenReturn(new WriteResult(1, true, false, null));

        postAs("alice", "/api/sql/confirm", Map.of("sql", UPDATE, "previewToken", token)).andExpect(status().isOk());
        postAs("alice", "/api/sql/confirm", Map.of("sql", UPDATE, "previewToken", token)).andExpect(status().isConflict());
    }

    @Test
    void listsTables() throws Exception {
        when(tools.call("list_tables", Map.of(), TableInfo[].class))
                .thenReturn(new TableInfo[] {new TableInfo("shop", "orders", null, "Orders")});

        mvc.perform(get("/api/schema/tables").with(user("alice")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].table").value("orders"));
    }

    @Test
    void mapsARejectionTo422WithItsRule() throws Exception {
        when(tools.call(eq("run_select"), anyMap(), eq(SelectResult.class))).thenThrow(new McpToolException(
                "REJECTED[STATEMENT_NOT_ALLOWED]: DELETE statements are not permitted"));

        execute("DELETE FROM shop.orders")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("REJECTED"))
                .andExpect(jsonPath("$.ruleId").value("STATEMENT_NOT_ALLOWED"))
                .andExpect(jsonPath("$.message").value("DELETE statements are not permitted"));
    }

    @ParameterizedTest
    @CsvSource({
            "'TIMEOUT: Too slow', 504, TIMEOUT, Too slow",
            "'DB_ERROR: ERROR: bad column', 502, DB_ERROR, 'ERROR: bad column'",
            "'MCP_UNAVAILABLE: Server down', 503, MCP_UNAVAILABLE, Server down",
            "'MCP_BAD_RESPONSE: Unreadable', 502, MCP_BAD_RESPONSE, Unreadable",
            "'Tool input validation failed', 502, TOOL_ERROR, Tool input validation failed"
    })
    void mapsToolFailures(String failure, int status, String code, String message) throws Exception {
        when(tools.call(eq("run_select"), anyMap(), eq(SelectResult.class))).thenThrow(new McpToolException(failure));

        execute("SELECT 1")
                .andExpect(status().is(status))
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.message").value(message));
    }

    @Test
    void hidesUnexpectedFailures() throws Exception {
        when(tools.call(eq("run_select"), anyMap(), eq(SelectResult.class))).thenThrow(new IllegalStateException("internal detail"));

        execute("SELECT 1")
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL"))
                .andExpect(content().string(not(containsString("internal detail"))));
    }

    @Test
    void refusesBlankAndOversizedInput() throws Exception {
        postAs("alice", "/api/sql/generate", Map.of("question", " ")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        postAs("alice", "/api/sql/execute", Map.of("sql", "x".repeat(20_001))).andExpect(status().isBadRequest());
    }

    @Test
    void givesTheLlmNoMcpTools() {
        assertThat(context.getBeansOfType(ToolCallbackProvider.class)).isEmpty();
    }
}
