package com.example.mcpsql.server.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** The server refuses to start without an API key, so the MCP endpoint can never be left open. */
@Validated
@ConfigurationProperties("mcp.security")
public record McpSecurityProperties(@NotBlank String apiKey) {
}
