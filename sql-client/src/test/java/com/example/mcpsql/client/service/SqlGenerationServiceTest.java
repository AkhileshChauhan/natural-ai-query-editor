package com.example.mcpsql.client.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.mcpsql.client.dto.GeneratedSql;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.core.io.ClassPathResource;

class SqlGenerationServiceTest {

    private static final String SCHEMA = "shop.orders(order_id integer PK)";
    private static final String GOOD = """
            {"sql":"SELECT order_id FROM shop.orders","kind":"SELECT","explanation":"Lists orders","tablesUsed":["shop.orders"]}
            """;

    private final ChatModel model = mock(ChatModel.class);
    private final McpToolInvoker tools = mock(McpToolInvoker.class);
    private SqlGenerationService service;

    private static ChatResponse reply(String text) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
    }

    private void modelReplies(String first, String... rest) {
        var stubbing = when(model.call(any(Prompt.class))).thenReturn(reply(first));
        for (String text : rest) {
            stubbing = stubbing.thenReturn(reply(text));
        }
    }

    private Prompt sentPrompt() {
        ArgumentCaptor<Prompt> prompt = ArgumentCaptor.forClass(Prompt.class);
        verify(model).call(prompt.capture());
        return prompt.getValue();
    }

    @BeforeEach
    void setUp() {
        when(model.getOptions()).thenReturn(ChatOptions.builder().build());
        when(tools.text("schema_overview", Map.of())).thenReturn(SCHEMA);
        service = new SqlGenerationService(ChatClient.create(model), tools, new ClassPathResource("prompts/sql-generator.st"));
    }

    @Test
    void returnsTheParsedStatement() {
        modelReplies(GOOD);

        GeneratedSql generated = service.generate("list orders");

        assertThat(generated.sql()).isEqualTo("SELECT order_id FROM shop.orders");
        assertThat(generated.kind()).isEqualTo("SELECT");
        assertThat(generated.explanation()).isEqualTo("Lists orders");
        assertThat(generated.tablesUsed()).containsExactly("shop.orders");
    }

    @Test
    void sendsTheSchemaAndRulesButNoTools() {
        modelReplies(GOOD);

        service.generate("list orders");

        Prompt prompt = sentPrompt();
        assertThat(prompt.getSystemMessage().getText()).contains(SCHEMA, "Never produce DELETE", "schema-qualify");
        assertThat(prompt.getUserMessage().getText()).contains("list orders");
        if (prompt.getOptions() instanceof ToolCallingChatOptions options) {
            assertThat(options.getToolCallbacks()).isEmpty();
        }
    }

    @Test
    void keepsBracesInTheQuestionLiteral() {
        modelReplies(GOOD);

        service.generate("orders where notes contain {urgent}");

        assertThat(sentPrompt().getUserMessage().getText()).contains("{urgent}");
    }

    @Test
    void readsJsonInsideCodeFences() {
        modelReplies("Here you go:\n```json\n" + GOOD + "```\nHope that helps.");

        assertThat(service.generate("list orders").sql()).isEqualTo("SELECT order_id FROM shop.orders");
    }

    @Test
    void acceptsALowercaseKind() {
        modelReplies(GOOD.replace("\"SELECT\"", "\"select\""));

        assertThat(service.generate("list orders").kind()).isEqualTo("SELECT");
    }

    @Test
    void defaultsMissingTablesToEmpty() {
        modelReplies("{\"sql\":\"SELECT 1\",\"kind\":\"SELECT\",\"explanation\":\"One\"}");

        assertThat(service.generate("one").tablesUsed()).isEmpty();
    }

    @Test
    void passesUnsupportedThroughWithNoSql() {
        modelReplies("{\"sql\":\"DELETE FROM shop.orders\",\"kind\":\"UNSUPPORTED\",\"explanation\":\"Deleting is not allowed\"}");

        GeneratedSql generated = service.generate("delete orders");

        assertThat(generated.sql()).isEmpty();
        assertThat(generated.kind()).isEqualTo("UNSUPPORTED");
        assertThat(generated.explanation()).isEqualTo("Deleting is not allowed");
    }

    @Test
    void retriesOnceAfterInvalidJson() {
        modelReplies("not json at all", GOOD);

        assertThat(service.generate("list orders").kind()).isEqualTo("SELECT");
        verify(model, times(2)).call(any(Prompt.class));
    }

    @Test
    void retriesWhenTheKindDoesNotMatchTheStatement() {
        modelReplies(GOOD.replace("\"SELECT\"", "\"UPDATE\""), GOOD);

        assertThat(service.generate("list orders").kind()).isEqualTo("SELECT");
        verify(model, times(2)).call(any(Prompt.class));
    }

    @Test
    void retriesWhenTheReplyHasSeveralStatements() {
        modelReplies(GOOD.replace("shop.orders\",\"kind", "shop.orders; DROP TABLE x\",\"kind"), GOOD);

        assertThat(service.generate("list orders").sql()).isEqualTo("SELECT order_id FROM shop.orders");
    }

    @Test
    void retriesWhenTheKindIsNotOneWeKnow() {
        modelReplies(GOOD.replace("\"SELECT\"", "\"DELETE\""), GOOD);

        assertThat(service.generate("list orders").kind()).isEqualTo("SELECT");
    }

    @Test
    void retriesWhenTheSqlIsEmpty() {
        modelReplies("{\"sql\":\"\",\"kind\":\"SELECT\",\"explanation\":\"\"}", GOOD);

        assertThat(service.generate("list orders").kind()).isEqualTo("SELECT");
    }

    @Test
    void givesUpAfterTheSecondFailure() {
        modelReplies("nope", "still nope");

        assertThatThrownBy(() -> service.generate("list orders")).isInstanceOf(SqlGenerationException.class);
        verify(model, times(2)).call(any(Prompt.class));
    }

    @Test
    void treatsAModelErrorAsAFailedAttempt() {
        when(model.call(any(Prompt.class))).thenThrow(new IllegalStateException("429 with secret detail"));

        assertThatThrownBy(() -> service.generate("list orders"))
                .isInstanceOf(SqlGenerationException.class)
                .hasMessageNotContaining("secret detail");
    }

    @Test
    void reportsAnUnreachableServerAsAToolFailure() {
        when(tools.text("schema_overview", Map.of())).thenThrow(new McpToolException("MCP_UNAVAILABLE: down"));

        assertThatThrownBy(() -> service.generate("list orders")).isInstanceOf(McpToolException.class);
    }
}
