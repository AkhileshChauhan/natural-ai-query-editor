package com.example.mcpsql.server.tools;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.example.mcpsql.server.audit.AuditLogger;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class WriteToolsIT {

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
    JdbcTemplate jdbc;

    McpTestClient client;
    ListAppender<ILoggingEvent> auditLog;

    @BeforeEach
    void connect() {
        client = new McpTestClient(port);
        auditLog = new ListAppender<>();
        auditLog.start();
        ((Logger) LoggerFactory.getLogger(AuditLogger.LOGGER_NAME)).addAppender(auditLog);
    }

    @AfterEach
    void disconnect() {
        ((Logger) LoggerFactory.getLogger(AuditLogger.LOGGER_NAME)).detachAppender(auditLog);
        client.close();
    }

    private int stockOf(String sku) {
        return jdbc.queryForObject("SELECT stock_qty FROM shop.products WHERE sku = ?", Integer.class, sku);
    }

    private JsonNode update(String sql, boolean dryRun) {
        return client.ok("run_update", Map.of("sql", sql, "dryRun", dryRun));
    }

    private String lastAuditLine() {
        return auditLog.list.getLast().getFormattedMessage();
    }

    @Test
    void dryRunReportsTheCountAndChangesNothing() {
        int before = stockOf("EL-1004");

        JsonNode preview = update("UPDATE shop.products SET stock_qty = stock_qty + 25 WHERE sku = 'EL-1004'", true);

        assertThat(preview.get("affectedRows").asInt()).isEqualTo(1);
        assertThat(preview.get("committed").asBoolean()).isFalse();
        assertThat(preview.get("dryRun").asBoolean()).isTrue();
        assertThat(stockOf("EL-1004")).isEqualTo(before);
    }

    @Test
    void commitPersistsTheChange() {
        int before = stockOf("EL-1004");

        JsonNode result = update("UPDATE shop.products SET stock_qty = stock_qty + 25 WHERE sku = 'EL-1004'", false);

        assertThat(result.get("committed").asBoolean()).isTrue();
        assertThat(result.get("dryRun").asBoolean()).isFalse();
        assertThat(result.get("returning").isNull()).isTrue();
        assertThat(stockOf("EL-1004")).isEqualTo(before + 25);
    }

    @Test
    void omittingDryRunIsRefusedAndChangesNothing() {
        int before = stockOf("BK-2001");
        String sql = "UPDATE shop.products SET stock_qty = stock_qty + 1 WHERE sku = 'BK-2001'";

        assertThat(client.error("run_update", Map.of("sql", sql))).contains("dryRun");
        Map<String, Object> nullDryRun = new HashMap<>(Map.of("sql", sql));
        nullDryRun.put("dryRun", null);
        assertThat(client.error("run_update", nullDryRun)).contains("dryRun");

        assertThat(stockOf("BK-2001")).isEqualTo(before);
    }

    @Test
    void updatesEveryRowWithoutWhere() {
        assertThat(update("UPDATE shop.products SET stock_qty = stock_qty", true).get("affectedRows").asInt()).isEqualTo(16);
        assertThat(update("UPDATE shop.products SET stock_qty = stock_qty", false).get("affectedRows").asInt()).isEqualTo(16);
    }

    @Test
    void updatesTablesOutsideTheInsertAllowlist() {
        assertThat(update("UPDATE shop.employees SET job_title = job_title WHERE employee_id = 1", false)
                .get("affectedRows").asInt()).isEqualTo(1);
    }

    @Test
    void returnsRowsFromReturningEvenInADryRun() {
        JsonNode result = update("UPDATE shop.products SET stock_qty = 7 WHERE sku = 'EL-1004' RETURNING sku, stock_qty", true);

        JsonNode returning = result.get("returning");
        assertThat(result.get("affectedRows").asInt()).isEqualTo(1);
        assertThat(returning.get("columns").toString()).isEqualTo("[\"sku\",\"stock_qty\"]");
        assertThat(returning.get("rows").toString()).isEqualTo("[[\"EL-1004\",7]]");
        assertThat(stockOf("EL-1004")).isNotEqualTo(7);
    }

    @Test
    void returningCountsAllRowsButSendsBackOnlyTheCap() {
        String sql = "INSERT INTO shop.customers (first_name, last_name, email, city) "
                + "SELECT 'A', 'B', 'bulk' || g || '@example.com', 'X' FROM generate_series(1, 600) g RETURNING customer_id";

        JsonNode result = client.ok("run_insert", Map.of("sql", sql, "dryRun", true));

        assertThat(result.get("affectedRows").asInt()).isEqualTo(600);
        assertThat(result.get("returning").get("rowCount").asInt()).isEqualTo(500);
        assertThat(result.get("returning").get("truncated").asBoolean()).isTrue();
    }

    @Test
    void insertPreviewsAndCommits() {
        String sql = "INSERT INTO shop.customers (first_name, last_name, email, city, state) "
                + "VALUES ('Jane', 'Doe', 'jane.doe@example.com', 'Dallas', 'TX') RETURNING customer_id";
        String count = "SELECT count(*) FROM shop.customers WHERE email = 'jane.doe@example.com'";

        assertThat(client.ok("run_insert", Map.of("sql", sql, "dryRun", true)).get("affectedRows").asInt()).isEqualTo(1);
        assertThat(jdbc.queryForObject(count, Integer.class)).isZero();

        assertThat(client.ok("run_insert", Map.of("sql", sql, "dryRun", false)).get("committed").asBoolean()).isTrue();
        assertThat(jdbc.queryForObject(count, Integer.class)).isEqualTo(1);
    }

    @Test
    void rejectsWhatTheGuardBlocks() {
        assertThat(client.error("run_update", Map.of("sql", "DELETE FROM shop.orders", "dryRun", false)))
                .contains("REJECTED[STATEMENT_NOT_ALLOWED]: DELETE statements are not permitted");
        assertThat(client.error("run_update", Map.of("sql", "SELECT 1", "dryRun", false)))
                .contains("REJECTED[WRONG_STATEMENT_KIND]");
        assertThat(client.error("run_insert", Map.of(
                "sql", "UPDATE shop.products SET stock_qty = 1 WHERE product_id = 1", "dryRun", false)))
                .contains("REJECTED[WRONG_STATEMENT_KIND]");
        assertThat(client.error("run_insert", Map.of(
                "sql", "INSERT INTO shop.payments (order_id, amount, method) VALUES (1, 1, 'CARD')", "dryRun", false)))
                .contains("REJECTED[INSERT_TABLE_NOT_ALLOWED]");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM shop.orders", Integer.class)).isEqualTo(20);
    }

    @Test
    void reportsConstraintViolationsAsDatabaseErrorsAndRollsBack() {
        int before = stockOf("EL-1004");

        String message = client.error("run_update",
                Map.of("sql", "UPDATE shop.products SET stock_qty = -1 WHERE sku = 'EL-1004'", "dryRun", false));

        assertThat(message).contains("DB_ERROR").doesNotContain("\tat ", "Exception");
        assertThat(stockOf("EL-1004")).isEqualTo(before);
    }

    @Test
    void auditRecordsACommittedWriteWithoutItsResultRows() {
        update("UPDATE shop.products SET stock_qty = stock_qty WHERE sku = 'SP-4003' RETURNING name", false);

        String productName = jdbc.queryForObject("SELECT name FROM shop.products WHERE sku = ?", String.class, "SP-4003");
        assertThat(lastAuditLine()).contains(
                "\"caller\":\"" + McpTestClient.CLIENT_NAME + "\"",
                "\"tool\":\"run_update\"",
                "\"decision\":\"ALLOWED\"",
                "\"dryRun\":false",
                "\"rows\":1",
                "durationMs");
        assertThat(lastAuditLine()).doesNotContain(productName);
    }

    @Test
    void auditRecordsRejectionsAndDatabaseErrors() {
        client.error("run_select", Map.of("sql", "DELETE FROM shop.orders"));
        assertThat(lastAuditLine()).contains(
                "\"tool\":\"run_select\"",
                "\"sql\":\"DELETE FROM shop.orders\"",
                "\"decision\":\"REJECTED\"",
                "\"ruleId\":\"STATEMENT_NOT_ALLOWED\"");

        client.error("run_select", Map.of("sql", "SELECT no_such_column FROM shop.orders"));
        assertThat(lastAuditLine()).contains("\"decision\":\"ALLOWED\"", "\"errorCode\":\"DB_ERROR\"");
    }

    @Test
    void auditCoversSchemaToolsAndStripsCommentsFromSql() {
        client.ok("list_tables", new HashMap<>());
        assertThat(lastAuditLine()).contains("\"tool\":\"list_tables\"", "\"rows\":7").doesNotContain("\"sql\"");

        client.ok("run_select", Map.of("sql", "SELECT 1 -- note"));
        assertThat(lastAuditLine()).contains("\"sql\":\"SELECT 1\"").doesNotContain("note");
    }
}
