package com.example.mcpsql.server.tools;

import static org.assertj.core.api.Assertions.assertThat;

import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "sqlguard.read-only=true")
@Testcontainers
class ReadOnlyModeIT {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine")
            .withDatabaseName("shopdb");

    @DynamicPropertySource
    static void dbProperties(DynamicPropertyRegistry registry) {
        registry.add("app.db.url", POSTGRES::getJdbcUrl);
        registry.add("app.db.admin-user", POSTGRES::getUsername);
        registry.add("app.db.admin-password", POSTGRES::getPassword);
    }

    @Value("${local.server.port}")
    int port;

    @Autowired
    ApplicationContext context;

    @Test
    void offersNoWriteTools() {
        assertThat(context.getBeansOfType(WriteTools.class)).isEmpty();

        try (McpTestClient client = new McpTestClient(port)) {
            assertThat(client.tools().stream().map(McpSchema.Tool::name))
                    .containsExactlyInAnyOrder("list_tables", "describe_table", "schema_overview", "run_select");
        }
    }
}
