package com.example.mcpsql.server.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.mcpsql.server.exec.QueryExecutionException;
import com.example.mcpsql.server.exec.QueryExecutor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest(properties = "sqlguard.query-timeout-seconds=1")
@Testcontainers
class QueryTimeoutIT {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine")
            .withDatabaseName("shopdb");

    @DynamicPropertySource
    static void dbProperties(DynamicPropertyRegistry registry) {
        registry.add("app.db.url", POSTGRES::getJdbcUrl);
        registry.add("app.db.admin-user", POSTGRES::getUsername);
        registry.add("app.db.admin-password", POSTGRES::getPassword);
    }

    @Autowired
    QueryExecutor executor;

    @Test
    void cancelsLongQueriesAndTheConnectionStaysUsable() {
        String heavy = "SELECT count(*) FROM generate_series(1, 100000000) a CROSS JOIN generate_series(1, 100000000) b";

        assertThatThrownBy(() -> executor.select(heavy, null))
                .isInstanceOf(QueryExecutionException.class)
                .hasMessageStartingWith("TIMEOUT:");
        assertThat(executor.select("SELECT 1", null).rowCount()).isEqualTo(1);
    }
}
