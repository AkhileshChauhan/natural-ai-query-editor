package com.example.mcpsql.client.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpRequest;
import org.junit.jupiter.api.Test;

class McpClientConfigTest {

    @Test
    void addsTheApiKeyToEveryRequest() {
        URI uri = URI.create("http://localhost:8081/sse");
        HttpRequest.Builder request = HttpRequest.newBuilder(uri);

        McpClientConfig.apiKeyHeader("secret").customize(request, "GET", uri, null, null);

        assertThat(request.build().headers().firstValue("X-API-Key")).contains("secret");
    }
}
