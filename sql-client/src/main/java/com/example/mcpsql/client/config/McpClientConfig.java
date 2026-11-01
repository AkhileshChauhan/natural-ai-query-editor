package com.example.mcpsql.client.config;

import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import org.springframework.ai.mcp.customizer.McpClientCustomizer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(McpConnectionProperties.class)
class McpClientConfig {

    private static final String API_KEY_HEADER = "X-API-Key";

    /** Applies to every MCP request, including the long-lived SSE stream. */
    @Bean
    McpClientCustomizer<HttpClientSseClientTransport.Builder> apiKeyHeader(McpConnectionProperties properties) {
        return (connection, transport) -> transport.httpRequestCustomizer(
                (request, method, uri, body, context) -> request.header(API_KEY_HEADER, properties.apiKey()));
    }
}
