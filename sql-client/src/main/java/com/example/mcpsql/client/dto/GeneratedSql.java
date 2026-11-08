package com.example.mcpsql.client.dto;

import java.util.List;

/** What the LLM returns. {@code sql} is empty when {@code kind} is UNSUPPORTED. */
public record GeneratedSql(String sql, String kind, String explanation, List<String> tablesUsed) {

    public GeneratedSql {
        tablesUsed = tablesUsed == null ? List.of() : List.copyOf(tablesUsed);
    }
}
