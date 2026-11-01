package com.example.mcpsql.server.tools;

import static org.assertj.core.api.Assertions.assertThat;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Calls the tools the way MCP Inspector and the client do: over the wire, through a real MCP session. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class McpToolsIT {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine")
            .withDatabaseName("shopdb");

    @DynamicPropertySource
    static void dbProperties(DynamicPropertyRegistry registry) {
        registry.add("app.db.url", POSTGRES::getJdbcUrl);
        registry.add("app.db.admin-user", POSTGRES::getUsername);
        registry.add("app.db.admin-password", POSTGRES::getPassword);
    }

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Value("${local.server.port}")
    int port;

    McpSyncClient client;

    @BeforeEach
    void connect() {
        client = McpClient.sync(HttpClientSseClientTransport.builder("http://localhost:" + port).build()).build();
        client.initialize();
    }

    @AfterEach
    void disconnect() {
        client.closeGracefully();
    }

    private McpSchema.CallToolResult call(String tool, Map<String, Object> arguments) {
        return client.callTool(new McpSchema.CallToolRequest(tool, arguments));
    }

    private static String text(McpSchema.CallToolResult result) {
        return ((McpSchema.TextContent) result.content().getFirst()).text();
    }

    private JsonNode ok(String tool, Map<String, Object> arguments) {
        McpSchema.CallToolResult result = call(tool, arguments);
        assertThat(result.isError()).as(text(result)).isFalse();
        return JSON.readTree(text(result));
    }

    private JsonNode select(String sql) {
        return ok("run_select", Map.of("sql", sql));
    }

    private String error(String sql) {
        McpSchema.CallToolResult result = call("run_select", Map.of("sql", sql));
        assertThat(result.isError()).as(text(result)).isTrue();
        return text(result);
    }

    private static List<String> firstColumn(JsonNode result) {
        return StreamSupport.stream(result.get("rows").spliterator(), false)
                .map(row -> row.get(0).asString())
                .toList();
    }

    @Test
    void listsExactlyTheReadAndSchemaTools() {
        List<String> names = client.listTools().tools().stream().map(McpSchema.Tool::name).toList();

        assertThat(names).containsExactlyInAnyOrder("list_tables", "describe_table", "schema_overview", "run_select");
    }

    @Test
    void toolDescriptionsStateDialectAndRejections() {
        McpSchema.Tool tool = client.listTools().tools().stream()
                .filter(candidate -> candidate.name().equals("run_select"))
                .findFirst().orElseThrow();

        assertThat(tool.description()).contains("PostgreSQL", "DELETE", "TRUNCATE", "DDL");
        assertThat(JSON.valueToTree(tool.inputSchema()).get("required").toString()).isEqualTo("[\"sql\"]");
    }

    @Test
    void listTablesReturnsEveryTable() {
        JsonNode tables = ok("list_tables", Map.of());

        assertThat(StreamSupport.stream(tables.spliterator(), false).map(t -> t.get("table").asString()))
                .containsExactly("categories", "customers", "employees", "order_items", "orders", "payments", "products");
        assertThat(tables.get(2).get("schema").asString()).isEqualTo("shop");
        assertThat(tables.get(2).get("comment").asString()).startsWith("Staff");
    }

    @ParameterizedTest
    @CsvSource({"shop.orders", "orders", "SHOP.ORDERS", "'\"orders\"'", "' orders '"})
    void describeTableAcceptsBothNameForms(String table) {
        JsonNode detail = ok("describe_table", Map.of("table", table));

        assertThat(detail.get("table").asString()).isEqualTo("orders");
    }

    @Test
    void describeTableReportsColumnsKeysAndIndexes() {
        JsonNode detail = ok("describe_table", Map.of("table", "shop.orders"));

        JsonNode first = detail.get("columns").get(0);
        assertThat(first.get("name").asString()).isEqualTo("order_id");
        assertThat(first.get("primaryKey").asBoolean()).isTrue();
        assertThat(first.get("nullable").asBoolean()).isFalse();
        assertThat(first.get("defaultValue").asString()).contains("identity");
        assertThat(StreamSupport.stream(detail.get("foreignKeys").spliterator(), false)
                .map(fk -> fk.get("referencedTable").asString()))
                .containsExactlyInAnyOrder("shop.customers", "shop.employees");
        assertThat(detail.get("indexes").isEmpty()).isFalse();
    }

    @ParameterizedTest
    @CsvSource({"nope", "public.orders", "pg_catalog.pg_class", "'orders; DROP TABLE x'"})
    void describeTableRefusesUnknownTables(String table) {
        McpSchema.CallToolResult result = call("describe_table", Map.of("table", table));

        assertThat(result.isError()).isTrue();
        assertThat(text(result)).contains("Unknown table");
    }

    @Test
    void schemaOverviewIsPlainTextWithForeignKeysAndRules() {
        String overview = text(call("schema_overview", Map.of()));

        assertThat(overview).startsWith("shop.categories(");
        assertThat(overview).contains("\n", "customer_id integer NOT NULL FK->shop.customers.customer_id");
        assertThat(overview).contains("Order total = SUM(quantity * unit_price * (1 - discount_pct / 100))");
        assertThat(overview).contains("Revenue excludes");
    }

    @Test
    void returnsColumnsRowsAndCounts() {
        JsonNode result = select("SELECT customer_id, city FROM shop.customers WHERE customer_id = 1");

        assertThat(result.get("columns").toString()).isEqualTo("[\"customer_id\",\"city\"]");
        assertThat(result.get("rowCount").asInt()).isEqualTo(1);
        assertThat(result.get("rows").get(0).get(0).asInt()).isEqualTo(1);
        assertThat(result.get("truncated").asBoolean()).isFalse();
        assertThat(result.has("elapsedMs")).isTrue();
    }

    @Test
    void knownAnswersFromTheSeedData() {
        assertThat(firstColumn(select("SELECT count(*) FROM shop.orders"))).containsExactly("20");
        assertThat(firstColumn(select("SELECT count(*) FROM shop.customers"))).containsExactly("12");
        assertThat(firstColumn(select("""
                SELECT c.customer_id FROM shop.customers c
                LEFT JOIN shop.orders o ON o.customer_id = c.customer_id
                WHERE o.order_id IS NULL ORDER BY 1
                """))).containsExactly("8", "12");
        assertThat(firstColumn(select("""
                SELECT round(sum(quantity * unit_price * (1 - discount_pct / 100)), 2)
                FROM shop.order_items WHERE order_id = 2
                """))).containsExactly("1272.53");
    }

    @Test
    void sampleQuestionsRun() {
        assertThat(select("""
                SELECT c.first_name, SUM(oi.quantity * oi.unit_price * (1 - oi.discount_pct / 100)) AS total
                FROM shop.customers c JOIN shop.orders o ON o.customer_id = c.customer_id
                JOIN shop.order_items oi ON oi.order_id = o.order_id
                WHERE o.status <> 'CANCELLED' AND EXTRACT(YEAR FROM o.order_date) = 2025
                GROUP BY c.customer_id, c.first_name ORDER BY total DESC LIMIT 5
                """).get("rowCount").asInt()).isEqualTo(5);
        assertThat(select("""
                SELECT e.first_name, m.first_name AS manager FROM shop.employees e
                LEFT JOIN shop.employees m ON m.employee_id = e.manager_id
                """).get("rowCount").asInt()).isEqualTo(7);
        assertThat(select("""
                WITH RECURSIVE h AS (
                    SELECT employee_id, 1 AS depth FROM shop.employees WHERE manager_id IS NULL
                    UNION ALL
                    SELECT e.employee_id, h.depth + 1 FROM shop.employees e JOIN h ON e.manager_id = h.employee_id
                ) SELECT max(depth) FROM h
                """).get("rows").get(0).get(0).asInt()).isEqualTo(3);
    }

    @Test
    void serializesEachTypeAsTheContractSays() {
        JsonNode row = select("""
                SELECT 12.50::numeric AS money, DATE '2025-01-15' AS day, TIMESTAMP '2025-01-15 10:30:00' AS local_time,
                       TIMESTAMPTZ '2025-01-15 10:30:00+00' AS instant, '\\x6869'::bytea AS raw,
                       '{"a": 1}'::jsonb AS doc, NULL::text AS missing, true AS flag, ARRAY[1, 2] AS list
                """).get("rows").get(0);

        assertThat(row.get(0).isString()).isTrue();
        assertThat(row.get(0).asString()).isEqualTo("12.50");
        assertThat(row.get(1).asString()).isEqualTo("2025-01-15");
        assertThat(row.get(2).asString()).isEqualTo("2025-01-15T10:30");
        assertThat(row.get(3).asString()).isEqualTo("2025-01-15T10:30:00Z");
        assertThat(row.get(4).asString()).isEqualTo("aGk=");
        assertThat(row.get(5).isString()).isTrue();
        assertThat(row.get(5).asString()).isEqualTo("{\"a\": 1}");
        assertThat(row.get(6).isNull()).isTrue();
        assertThat(row.get(7).asBoolean()).isTrue();
        assertThat(row.get(8).toString()).isEqualTo("[1,2]");
    }

    @Test
    void keepsDuplicateColumnNames() {
        JsonNode result = select("SELECT 1 AS id, 2 AS id");

        assertThat(result.get("columns").toString()).isEqualTo("[\"id\",\"id\"]");
        assertThat(result.get("rows").get(0).toString()).isEqualTo("[1,2]");
    }

    @Test
    void capsRowsAndFlagsTruncation() {
        JsonNode capped = select("SELECT g FROM generate_series(1, 600) g");
        assertThat(capped.get("rowCount").asInt()).isEqualTo(500);
        assertThat(capped.get("truncated").asBoolean()).isTrue();

        JsonNode exact = select("SELECT g FROM generate_series(1, 500) g");
        assertThat(exact.get("rowCount").asInt()).isEqualTo(500);
        assertThat(exact.get("truncated").asBoolean()).isFalse();
    }

    @Test
    void maxRowsLowersTheCapButCannotRaiseIt() {
        Map<String, Object> lower = new HashMap<>(Map.of("sql", "SELECT g FROM generate_series(1, 600) g", "maxRows", 3));
        assertThat(ok("run_select", lower).get("rowCount").asInt()).isEqualTo(3);

        lower.put("maxRows", 10_000);
        assertThat(ok("run_select", lower).get("rowCount").asInt()).isEqualTo(500);
    }

    @Test
    void trailingCommentAndSemicolonDoNotDisableTheCap() {
        assertThat(select("SELECT g FROM generate_series(1, 600) g; -- all of them").get("rowCount").asInt()).isEqualTo(500);
    }

    @Test
    void readsSystemCatalogsAndUsesTheJsonQuestionMarkOperator() {
        assertThat(firstColumn(select("SELECT count(*) FROM pg_catalog.pg_class WHERE relname = 'orders'")))
                .containsExactly("1");
        assertThat(firstColumn(select("SELECT '{\"a\": 1}'::jsonb ? 'a'"))).containsExactly("true");
    }

    @Test
    void reportsGuardRejectionsWithTheRuleId() {
        assertThat(error("DELETE FROM shop.orders")).contains("REJECTED[STATEMENT_NOT_ALLOWED]: DELETE statements are not permitted");
        assertThat(error("SELECT 1; DROP TABLE x")).contains("REJECTED[MULTIPLE_STATEMENTS]");
        assertThat(error("UPDATE shop.products SET stock_qty = 1 WHERE product_id = 1")).contains("REJECTED[WRONG_STATEMENT_KIND]");
        assertThat(error("WITH d AS (DELETE FROM shop.orders RETURNING *) SELECT * FROM d")).contains("REJECTED[DATA_MODIFYING_CTE]");
    }

    @Test
    void theDatabaseRefusesWritesInsideASelect() {
        assertThat(error("SELECT order_id FROM shop.orders FOR UPDATE")).contains("DB_ERROR", "read-only transaction");
        assertThat(error("SELECT nextval('shop.orders_order_id_seq')")).contains("DB_ERROR", "read-only transaction");
        assertThat(error("SELECT setval('shop.orders_order_id_seq', 1)")).contains("DB_ERROR", "permission denied");
    }

    @Test
    void reportsDatabaseErrorsOnOneLineWithoutTheQuery() {
        String message = error("SELECT no_such_column FROM shop.orders");

        assertThat(message).contains("DB_ERROR", "no_such_column");
        assertThat(message).doesNotContain("\n", "Exception", "\tat ");
    }
}
