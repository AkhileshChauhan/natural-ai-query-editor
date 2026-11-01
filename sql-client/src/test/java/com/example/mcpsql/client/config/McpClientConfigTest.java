package com.example.mcpsql.client.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.client.transport.customizer.McpSyncHttpClientRequestCustomizer;
import java.net.URI;
import java.net.http.HttpRequest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class McpClientConfigTest {

    @Test
    void addsTheApiKeyToEveryRequest() {
        HttpClientSseClientTransport.Builder transport = mock(HttpClientSseClientTransport.Builder.class);

        new McpClientConfig().apiKeyHeader(new McpConnectionProperties("secret")).customize("sql", transport);

        ArgumentCaptor<McpSyncHttpClientRequestCustomizer> customizer =
                ArgumentCaptor.forClass(McpSyncHttpClientRequestCustomizer.class);
        verify(transport).httpRequestCustomizer(customizer.capture());
        URI uri = URI.create("http://localhost:8081/sse");
        HttpRequest.Builder request = HttpRequest.newBuilder(uri);
        customizer.getValue().customize(request, "GET", uri, null, null);
        assertThat(request.build().headers().firstValue("X-API-Key")).contains("secret");
    }
}
