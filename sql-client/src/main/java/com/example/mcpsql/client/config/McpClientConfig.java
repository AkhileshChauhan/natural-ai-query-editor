package com.example.mcpsql.client.config;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.client.transport.customizer.McpSyncHttpClientRequestCustomizer;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.function.Supplier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Builds MCP connections by hand instead of using Spring AI's auto-configured client, which is one fixed
 * bean that cannot recover once the server restarts. {@code McpToolInvoker} asks for a new one when needed.
 */
@Configuration
@EnableConfigurationProperties(McpConnectionProperties.class)
class McpClientConfig {

    private static final String API_KEY_HEADER = "X-API-Key";
    private static final String CLIENT_NAME = "sql-client";
    private static final String CLIENT_VERSION = "1.0.0";

    @Bean
    Supplier<McpSyncClient> mcpConnections(McpConnectionProperties properties) {
        return () -> McpClient.sync(HttpClientSseClientTransport.builder(properties.url())
                        .httpRequestCustomizer(apiKeyHeader(properties.apiKey()))
                        .build())
                .clientInfo(new McpSchema.Implementation(CLIENT_NAME, CLIENT_VERSION))
                .initializationTimeout(properties.connectTimeout())
                .requestTimeout(properties.requestTimeout())
                .build();
    }

    /** Applies to every MCP request, including the long-lived SSE stream. */
    static McpSyncHttpClientRequestCustomizer apiKeyHeader(String apiKey) {
        return (request, method, uri, body, context) -> request.header(API_KEY_HEADER, apiKey);
    }
}
