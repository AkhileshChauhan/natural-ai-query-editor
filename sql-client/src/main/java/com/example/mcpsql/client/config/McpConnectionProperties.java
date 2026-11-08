package com.example.mcpsql.client.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Where mcp-sql-server is, and the key it expects in its {@code X-API-Key} header. */
@Validated
@ConfigurationProperties("app.mcp")
public record McpConnectionProperties(
        @NotBlank String url,
        @NotBlank String apiKey,
        @NotNull Duration connectTimeout,
        @NotNull Duration requestTimeout) {
}
