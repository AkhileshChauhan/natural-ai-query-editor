package com.example.mcpsql.client.service;

import com.example.mcpsql.client.dto.GeneratedSql;
import com.example.mcpsql.client.dto.StatementKind;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

/**
 * Turns a question into SQL. The schema goes straight into the prompt, so no tool calls are needed and the
 * LLM has no tools at all. Only the question and the schema leave this application, never query results.
 */
@Service
public class SqlGenerationService {

    private static final Logger LOG = LoggerFactory.getLogger(SqlGenerationService.class);
    private static final int MAX_ATTEMPTS = 2;

    private final ChatClient chat;
    private final McpToolInvoker tools;
    private final Resource systemPrompt;

    public SqlGenerationService(ChatClient chat, McpToolInvoker tools,
            @Value("classpath:prompts/sql-generator.st") Resource systemPrompt) {
        this.chat = chat;
        this.tools = tools;
        this.systemPrompt = systemPrompt;
    }

    public GeneratedSql generate(String question) {
        String schema = tools.text("schema_overview", Map.of());
        RuntimeException failure = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                return validated(ask(question, schema));
            } catch (RuntimeException e) {
                failure = e;
                LOG.warn("SQL generation attempt {} failed: {}", attempt, e.getMessage());
            }
        }
        throw new SqlGenerationException(failure);
    }

    private GeneratedSql ask(String question, String schema) {
        return chat.prompt()
                .system(system -> system.text(systemPrompt).param("schema", schema))
                // A parameter, so braces in the question are not read as template variables
                .user(user -> user.text("{question}").param("question", question))
                .call()
                .entity(new GeneratedSqlConverter());
    }

    private static GeneratedSql validated(GeneratedSql reply) {
        if (reply == null || reply.kind() == null) {
            throw new IllegalArgumentException("The reply has no kind");
        }
        StatementKind kind = StatementKind.valueOf(reply.kind().strip().toUpperCase(Locale.ROOT));
        if (kind == StatementKind.UNSUPPORTED) {
            return new GeneratedSql("", kind.name(), reply.explanation(), reply.tablesUsed());
        }
        String sql = reply.sql() == null ? "" : reply.sql().strip();
        Optional<StatementKind> actual = SqlClassifier.classify(sql);
        if (actual.isEmpty() || actual.get() != kind) {
            throw new IllegalArgumentException("The reply's sql is not one " + kind + " statement");
        }
        return new GeneratedSql(sql, kind.name(), reply.explanation(), reply.tablesUsed());
    }
}
