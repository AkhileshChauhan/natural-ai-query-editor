package com.example.mcpsql.client.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** The key mcp-sql-server expects in its {@code X-API-Key} header. */
@Validated
@ConfigurationProperties("app.mcp")
public record McpConnectionProperties(@NotBlank String apiKey) {
}
