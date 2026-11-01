package com.example.mcpsql.server.guard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import com.example.mcpsql.server.guard.GuardResult.Allowed;
import com.example.mcpsql.server.guard.GuardResult.Rejected;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class SqlGuardTest {

    private static final List<String> INSERT_TABLES =
            List.of("shop.customers", "shop.orders", "shop.order_items", "shop.products");

    private final SqlGuard guard = new SqlGuard(INSERT_TABLES);

    private void assertRejected(String sql, String ruleId) {
        assertThat(guard.check(sql)).as(sql)
                .isInstanceOfSatisfying(Rejected.class, r -> assertThat(r.ruleId()).isEqualTo(ruleId));
    }

    private void assertAllowed(String sql, StatementKind kind) {
        assertThat(guard.check(sql)).as(sql)
                .isInstanceOfSatisfying(Allowed.class, a -> assertThat(a.kind()).isEqualTo(kind));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "SELECT 1",
            "select order_id from shop.orders;",
            "SELECT order_id FROM orders",
            "(SELECT order_id FROM shop.orders)",
            "SELECT 'DROP TABLE x; DELETE FROM y' AS note",
            "SELECT 'pg_sleep(60)', 'it''s' FROM shop.orders",
            "SELECT \"order_id\" FROM \"shop\".\"orders\"",
            "SELECT name FROM shop.products WHERE name ILIKE '%chair%'",
            "SELECT c.first_name, SUM(oi.quantity * oi.unit_price * (1 - oi.discount_pct / 100)) AS total "
                    + "FROM shop.customers c JOIN shop.orders o ON o.customer_id = c.customer_id "
                    + "JOIN shop.order_items oi ON oi.order_id = o.order_id "
                    + "WHERE o.status <> 'CANCELLED' AND EXTRACT(YEAR FROM o.order_date) = 2025 "
                    + "GROUP BY c.customer_id, c.first_name ORDER BY total DESC LIMIT 5",
            "SELECT cat.name, date_trunc('month', o.order_date)::date AS m, SUM(oi.quantity) "
                    + "FROM shop.orders o JOIN shop.order_items oi ON oi.order_id = o.order_id "
                    + "JOIN shop.products p ON p.product_id = oi.product_id "
                    + "JOIN shop.categories cat ON cat.category_id = p.category_id GROUP BY 1, 2",
            "SELECT e.first_name, m.first_name AS manager FROM shop.employees e "
                    + "LEFT JOIN shop.employees m ON m.employee_id = e.manager_id",
            "SELECT c.customer_id FROM shop.customers c LEFT JOIN shop.orders o ON o.customer_id = c.customer_id "
                    + "WHERE o.order_id IS NULL",
            "SELECT customer_id FROM shop.customers c WHERE NOT EXISTS "
                    + "(SELECT 1 FROM shop.orders o WHERE o.customer_id = c.customer_id)",
            "WITH RECURSIVE h AS (SELECT employee_id, manager_id, 1 AS depth FROM shop.employees "
                    + "WHERE manager_id IS NULL UNION ALL SELECT e.employee_id, e.manager_id, h.depth + 1 "
                    + "FROM shop.employees e JOIN h ON e.manager_id = h.employee_id) SELECT * FROM h",
            "WITH totals AS (SELECT order_id, SUM(quantity * unit_price) AS total FROM shop.order_items "
                    + "GROUP BY order_id), paid AS (SELECT order_id, SUM(amount) AS amount FROM shop.payments "
                    + "GROUP BY order_id) SELECT t.order_id FROM totals t LEFT JOIN paid p USING (order_id) "
                    + "WHERE COALESCE(p.amount, 0) < t.total",
            "SELECT order_id, RANK() OVER (PARTITION BY customer_id ORDER BY order_date DESC) FROM shop.orders",
            "SELECT order_id, SUM(amount) OVER w FROM shop.payments WINDOW w AS (ORDER BY paid_at)",
            "SELECT status, COUNT(*) FILTER (WHERE order_date > '2025-01-01') FROM shop.orders GROUP BY status",
            "SELECT status FROM shop.orders UNION SELECT method FROM shop.payments",
            "SELECT DISTINCT ON (customer_id) customer_id, order_date FROM shop.orders ORDER BY customer_id, order_date",
            "SELECT o.order_id, x.n FROM shop.orders o CROSS JOIN LATERAL (SELECT COUNT(*) AS n FROM "
                    + "shop.order_items i WHERE i.order_id = o.order_id) x",
            "SELECT g FROM generate_series(1, 5) AS g",
            "SELECT now() - INTERVAL '30 days', pg_typeof(1), pg_size_pretty(1024::bigint)",
            "SELECT order_id\nFROM shop.orders\n\nWHERE status = 'NEW'\n\nORDER BY order_id",
            "SELECT 1 -- trailing comment",
            "SELECT /* inline */ 1",
            "SELECT 'José' AS name",
            "SELECT 'TABLE' AS \"table\", name FROM shop.products",
    })
    void allowsReads(String sql) {
        assertAllowed(sql, StatementKind.SELECT);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "INSERT INTO shop.customers (first_name, last_name, email, city) "
                    + "VALUES ('Jane', 'Doe', 'jane@example.com', 'Dallas') RETURNING customer_id",
            "INSERT INTO customers (first_name, last_name, email, city) VALUES ('A', 'B', 'c@d', 'E')",
            "INSERT INTO shop.products (sku, name, category_id, unit_price) "
                    + "SELECT 'X-1', name, category_id, unit_price FROM shop.products WHERE product_id = 1",
            "INSERT INTO shop.order_items (order_id, product_id, quantity, unit_price) VALUES (1, 1, 1, 9.99) "
                    + "ON CONFLICT DO NOTHING",
            "WITH src AS (SELECT 1 AS id) INSERT INTO shop.orders (customer_id) SELECT id FROM src",
            "INSERT INTO shop.customers (first_name, last_name, email, city, phone) VALUES ('A', 'B', 'c@d', 'E', '555')",
    })
    void allowsInserts(String sql) {
        assertAllowed(sql, StatementKind.INSERT);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "UPDATE shop.products SET stock_qty = stock_qty + 25 WHERE sku = 'EL-1004'",
            "update products set is_active = false where product_id = 3 returning product_id",
            "UPDATE shop.orders o SET status = 'PAID' FROM shop.payments p WHERE p.order_id = o.order_id AND o.status = 'NEW'",
            "UPDATE shop.products SET unit_price = 1 WHERE 1 = 1 AND product_id = 5",
            "UPDATE shop.products SET unit_price = 1 WHERE product_id IN (1, 2) OR sku = 'X'",
            "UPDATE shop.orders SET notes = 'x' WHERE customer_id IN (SELECT customer_id FROM shop.customers WHERE city = 'Dallas')",
            "UPDATE shop.customers SET phone = '555' WHERE customer_id = 1",
            "WITH src AS (SELECT 1 AS id) UPDATE shop.products p SET stock_qty = 1 FROM src WHERE p.product_id = src.id",
    })
    void allowsUpdates(String sql) {
        assertAllowed(sql, StatementKind.UPDATE);
    }

    static Stream<Arguments> blocked() {
        return Stream.of(
                // Empty and oversized
                arguments("EMPTY_SQL", "   \n\t"),
                arguments("EMPTY_SQL", ""),
                arguments("EMPTY_SQL", "-- only a comment"),
                arguments("SQL_TOO_LONG", "SELECT '" + "x".repeat(20_001) + "'"),
                // Plain statements
                arguments("STATEMENT_NOT_ALLOWED", "DELETE FROM shop.orders"),
                arguments("STATEMENT_NOT_ALLOWED", "DELETE FROM shop.orders WHERE status = 'CANCELLED'"),
                arguments("STATEMENT_NOT_ALLOWED", "TRUNCATE shop.orders"),
                arguments("STATEMENT_NOT_ALLOWED", "DROP TABLE shop.orders"),
                arguments("STATEMENT_NOT_ALLOWED", "ALTER TABLE shop.orders ADD COLUMN x INT"),
                arguments("STATEMENT_NOT_ALLOWED", "CREATE TABLE x (a INT)"),
                arguments("STATEMENT_NOT_ALLOWED", "CREATE TABLE x AS SELECT * FROM shop.orders"),
                arguments("STATEMENT_NOT_ALLOWED", "COMMENT ON TABLE shop.orders IS 'x'"),
                arguments("STATEMENT_NOT_ALLOWED", "MERGE INTO shop.orders o USING shop.payments p ON (o.order_id = p.order_id) WHEN MATCHED THEN DELETE"),
                arguments("STATEMENT_NOT_ALLOWED", "COPY shop.orders TO PROGRAM 'ls'"),
                arguments("STATEMENT_NOT_ALLOWED", "COPY shop.orders FROM '/etc/passwd'"),
                arguments("STATEMENT_NOT_ALLOWED", "CALL p()"),
                arguments("STATEMENT_NOT_ALLOWED", "DO $$ BEGIN DELETE FROM shop.orders; END $$"),
                arguments("STATEMENT_NOT_ALLOWED", "PREPARE p AS SELECT 1"),
                arguments("STATEMENT_NOT_ALLOWED", "EXECUTE p"),
                arguments("STATEMENT_NOT_ALLOWED", "DEALLOCATE p"),
                arguments("STATEMENT_NOT_ALLOWED", "GRANT ALL ON shop.orders TO mcp_app"),
                arguments("STATEMENT_NOT_ALLOWED", "REVOKE ALL ON shop.orders FROM mcp_app"),
                arguments("STATEMENT_NOT_ALLOWED", "SET ROLE postgres"),
                arguments("STATEMENT_NOT_ALLOWED", "SET SESSION AUTHORIZATION postgres"),
                arguments("STATEMENT_NOT_ALLOWED", "SET statement_timeout = 0"),
                arguments("STATEMENT_NOT_ALLOWED", "RESET ALL"),
                arguments("STATEMENT_NOT_ALLOWED", "SHOW ALL"),
                arguments("STATEMENT_NOT_ALLOWED", "BEGIN"),
                arguments("STATEMENT_NOT_ALLOWED", "COMMIT"),
                arguments("STATEMENT_NOT_ALLOWED", "ROLLBACK"),
                arguments("STATEMENT_NOT_ALLOWED", "SAVEPOINT s"),
                arguments("STATEMENT_NOT_ALLOWED", "VACUUM"),
                arguments("STATEMENT_NOT_ALLOWED", "ANALYZE shop.orders"),
                arguments("STATEMENT_NOT_ALLOWED", "CLUSTER shop.orders"),
                arguments("STATEMENT_NOT_ALLOWED", "REINDEX TABLE shop.orders"),
                arguments("STATEMENT_NOT_ALLOWED", "LOCK TABLE shop.orders"),
                arguments("STATEMENT_NOT_ALLOWED", "LISTEN c"),
                arguments("STATEMENT_NOT_ALLOWED", "NOTIFY c"),
                arguments("STATEMENT_NOT_ALLOWED", "DISCARD ALL"),
                arguments("STATEMENT_NOT_ALLOWED", "EXPLAIN ANALYZE DELETE FROM shop.orders"),
                arguments("PARSE_ERROR", "INSERT INTO shop.orders TABLE shop.orders"),
                arguments("STATEMENT_NOT_ALLOWED", "WITH x AS (SELECT 1) DELETE FROM shop.orders"),
                arguments("STATEMENT_NOT_ALLOWED", "delete from shop.orders"),
                arguments("STATEMENT_NOT_ALLOWED", "DeLeTe FrOm shop.orders"),
                arguments("STATEMENT_NOT_ALLOWED", "/* hi */ DELETE FROM shop.orders"),
                arguments("STATEMENT_NOT_ALLOWED", "-- SELECT\nDELETE FROM shop.orders"),
                arguments("STATEMENT_NOT_ALLOWED", "/* /* nested */ */ DROP TABLE x"),
                arguments("STATEMENT_NOT_ALLOWED", "\n\t DELETE\n\tFROM\tshop.orders"),
                arguments("STATEMENT_NOT_ALLOWED", "D\"E\"LETE FROM shop.orders"),
                // More than one statement
                arguments("MULTIPLE_STATEMENTS", "SELECT 1; DROP TABLE x"),
                arguments("MULTIPLE_STATEMENTS", "SELECT 1; SELECT 2"),
                arguments("MULTIPLE_STATEMENTS", "SELECT 1;;"),
                arguments("MULTIPLE_STATEMENTS", "SELECT 1; /* x */ DELETE FROM shop.orders"),
                arguments("MULTIPLE_STATEMENTS", "SELECT '\\'; DROP TABLE x -- '"),
                arguments("MULTIPLE_STATEMENTS", "SELECT 1 /* /* */ ; DROP TABLE x -- */ */ ; SELECT 2"),
                // Data-modifying CTEs
                arguments("DATA_MODIFYING_CTE", "WITH d AS (DELETE FROM shop.orders RETURNING *) SELECT * FROM d"),
                arguments("DATA_MODIFYING_CTE", "WITH u AS (UPDATE shop.products SET stock_qty = 1 WHERE product_id = 1 RETURNING *) SELECT * FROM u"),
                arguments("DATA_MODIFYING_CTE", "WITH i AS (INSERT INTO shop.orders (customer_id) VALUES (1) RETURNING *) SELECT * FROM i"),
                arguments("DATA_MODIFYING_CTE", "WITH a AS (SELECT 1), d AS (DELETE FROM shop.orders RETURNING *) SELECT * FROM a"),
                arguments("DATA_MODIFYING_CTE", "WITH d AS (DELETE FROM shop.orders RETURNING order_id) INSERT INTO shop.products (sku) SELECT order_id FROM d"),
                arguments("DATA_MODIFYING_CTE", "WITH d AS (DELETE FROM shop.orders RETURNING order_id) UPDATE shop.products SET stock_qty = 1 WHERE product_id IN (SELECT order_id FROM d)"),
                arguments("DATA_MODIFYING_CTE", "INSERT INTO shop.products (sku) WITH d AS (DELETE FROM shop.orders RETURNING order_id) SELECT order_id FROM d"),
                arguments("DATA_MODIFYING_CTE", "SELECT * FROM (WITH d AS (DELETE FROM shop.orders RETURNING *) SELECT * FROM d) q"),
                arguments("DATA_MODIFYING_CTE", "SELECT 1 WHERE EXISTS (WITH d AS (DELETE FROM shop.orders RETURNING *) SELECT 1 FROM d)"),
                arguments("DATA_MODIFYING_CTE", "wItH d aS (dElEtE fRoM shop.orders rEtUrNiNg *) sElEcT * fRoM d"),
                arguments("DATA_MODIFYING_CTE", "WITH /* x */ d AS (/* y */ DELETE /* z */ FROM shop.orders RETURNING *) SELECT * FROM d"),
                // SELECT INTO
                arguments("SELECT_INTO", "SELECT * INTO x FROM shop.orders"),
                // Unsafe literals and characters
                arguments("UNSAFE_LITERAL", "SELECT $$x$$"),
                arguments("UNSAFE_LITERAL", "SELECT $tag$x$tag$"),
                arguments("UNSAFE_LITERAL", "SELECT $$ ' $$; DELETE FROM shop.orders -- '"),
                arguments("UNSAFE_LITERAL", "SELECT $1"),
                arguments("UNSAFE_LITERAL", "SELECT E'\\''; DELETE FROM shop.orders -- '"),
                arguments("UNSAFE_LITERAL", "SELECT U&\"x\\005fy\"(60)"),
                arguments("UNSAFE_LITERAL", "SELECT u&'x'"),
                arguments("INVALID_CHARACTER", "SELECT 1"),
                arguments("INVALID_CHARACTER", "SELECT 1​"),
                arguments("INVALID_CHARACTER", "SELECT 1\u0000"),
                arguments("INVALID_CHARACTER", "ＤＲＯＰ TABLE x"),
                arguments("UNTERMINATED_TOKEN", "SELECT 'x"),
                arguments("UNTERMINATED_TOKEN", "SELECT \"x"),
                arguments("UNTERMINATED_TOKEN", "SELECT 1 /* x"),
                arguments("UNTERMINATED_TOKEN", "SELECT 1 /* /* x */"),
                // Fail closed on what cannot be parsed
                arguments("PARSE_ERROR", "SELECT FROM WHERE"),
                arguments("PARSE_ERROR", "SELECT 1)"),
                arguments("PARSE_ERROR", "SELECT 1\n\nSELECT 2"),
                arguments("PARSE_ERROR", "(SELECT order_id FROM shop.orders) FOR UPDATE"),
                arguments("PARSE_ERROR", "SELECT order_id INTO TEMP x FROM shop.orders"),
                arguments("MULTIPLE_STATEMENTS", "SELECT 1) AS q; DROP TABLE x --"),
                arguments("PARSE_ERROR", "SELECT " + "(".repeat(2_000) + "1" + ")".repeat(2_000)),
                // INSERT
                arguments("INSERT_TABLE_NOT_ALLOWED", "INSERT INTO shop.payments (order_id, amount, method) VALUES (1, 1, 'CARD')"),
                arguments("INSERT_TABLE_NOT_ALLOWED", "INSERT INTO shop.employees (first_name) VALUES ('x')"),
                arguments("INSERT_TABLE_NOT_ALLOWED", "INSERT INTO public.orders (a) VALUES (1)"),
                arguments("ON_CONFLICT_UPDATE", "INSERT INTO shop.products (sku) VALUES ('x') ON CONFLICT (sku) DO UPDATE SET name = 'y'"));
    }

    @ParameterizedTest
    @MethodSource("blocked")
    void blocks(String ruleId, String sql) {
        assertRejected(sql, ruleId);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "UPDATE shop.employees SET salary = 1 WHERE employee_id = 1",
            "UPDATE employees SET job_title = 'Rep' WHERE manager_id = 2",
            "UPDATE shop.payments SET amount = 1 WHERE payment_id = 1",
            "UPDATE shop.categories SET description = 'x' WHERE category_id = 1",
            "UPDATE shop.customers SET city = 'Dallas' WHERE customer_id = 1",
            "UPDATE shop.orders SET status = 'PAID' WHERE order_id = 1",
            "UPDATE shop.order_items SET quantity = 2 WHERE order_item_id = 1",
            "UPDATE shop.products SET stock_qty = 1 WHERE product_id = 1",
            "UPDATE shop.products SET stock_qty = 1",
            "UPDATE shop.employees SET salary = salary * 2",
            "UPDATE shop.products SET stock_qty = 1 WHERE true",
            "UPDATE shop.products SET stock_qty = 1 WHERE 1 = 1",
            "UPDATE shop.products SET stock_qty = 1 WHERE product_id = 1 OR 1 = 1",
            "UPDATE shop.products SET stock_qty = 1 WHERE product_id = product_id",
            "UPDATE shop.products SET stock_qty = 1 RETURNING *",
            "UPDATE shop.products SET stock_qty = nextval('x') WHERE product_id = 1",
            "UPDATE pg_catalog.pg_authid SET rolsuper = true WHERE rolname = 'mcp_app'",
    })
    void updatesAreUnrestricted(String sql) {
        assertThat(guard.check(sql, StatementKind.UPDATE)).as(sql).isInstanceOf(Allowed.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "SELECT * FROM shop.customers",
            "SELECT * FROM customers",
            "SELECT phone FROM shop.customers WHERE phone LIKE '555%'",
            "SELECT row_to_json(c) FROM shop.customers c",
            "SELECT * FROM shop.employees",
            "SELECT * FROM shop.categories",
            "SELECT * FROM shop.orders",
            "SELECT * FROM shop.order_items",
            "SELECT * FROM shop.payments",
            "SELECT * FROM shop.products",
            "TABLE shop.orders",
            "VALUES (1), (2)",
            "SELECT * FROM (TABLE orders) q",
            "SELECT * FROM pg_catalog.pg_class",
            "SELECT * FROM pg_stat_activity",
            "SELECT table_name FROM information_schema.tables",
            "SELECT 1::pg_catalog.int4",
            "SELECT pg_sleep(1)",
            "SELECT pg_read_file('/etc/passwd')",
            "SELECT set_config('a.b', 'c', false)",
            "SELECT nextval('shop.orders_order_id_seq')",
            "SELECT query_to_xml('SELECT 1', true, true, '')",
            "SELECT pg_advisory_lock(1)",
            "SELECT order_id FROM shop.orders FOR UPDATE",
            "SELECT order_id FROM shop.orders FOR SHARE",
            "SELECT 1 UNION SELECT 2 FOR UPDATE",
    })
    void selectsAreUnrestricted(String sql) {
        assertThat(guard.check(sql, StatementKind.SELECT)).as(sql).isInstanceOf(Allowed.class);
    }

    @Test
    void textPostgresReadsAsCodeIsKeptEvenIfANaiveLexerSeesAComment() {
        assertThat(guard.check("SELECT 1 /* /* */ , ' */ , 2 -- '")).isInstanceOfSatisfying(Allowed.class,
                a -> assertThat(a.sql()).isEqualTo("SELECT 1   , 2"));
    }

    @ParameterizedTest
    @NullSource
    void rejectsNull(String sql) {
        assertRejected(sql, "EMPTY_SQL");
    }

    @Test
    void reasonNamesTheBlockedStatement() {
        assertThat(guard.check("DELETE FROM shop.orders WHERE status = 'CANCELLED'"))
                .isEqualTo(new Rejected("STATEMENT_NOT_ALLOWED", "DELETE statements are not permitted"));
    }

    @Test
    void reasonDoesNotEchoLongKeywords() {
        assertThat(guard.check("x".repeat(500))).isInstanceOfSatisfying(Rejected.class,
                r -> assertThat(r.reason()).isEqualTo("This statement is not permitted"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "SELECT 1 -- comment",
            "SELECT 1;",
            "SELECT 1 /* a /* nested */ b */ ;  ",
            "  /* leading */ SELECT 1",
    })
    void allowedSqlHasNoCommentsOrTerminatorSoItCanBeWrapped(String sql) {
        assertThat(guard.check(sql)).isInstanceOfSatisfying(Allowed.class,
                a -> assertThat(a.sql()).isEqualTo("SELECT 1"));
    }

    @Test
    void allowedSqlKeepsStringContent() {
        assertThat(guard.check("SELECT '-- not a comment; /* nor this */'")).isInstanceOfSatisfying(Allowed.class,
                a -> assertThat(a.sql()).isEqualTo("SELECT '-- not a comment; /* nor this */'"));
    }

    @Test
    void toolsAcceptOnlyTheirOwnKind() {
        String update = "UPDATE shop.products SET stock_qty = 1 WHERE product_id = 1";
        assertThat(guard.check("SELECT 1", StatementKind.SELECT)).isInstanceOf(Allowed.class);
        assertThat(guard.check(update, StatementKind.UPDATE)).isInstanceOf(Allowed.class);
        assertThat(guard.check(update, StatementKind.SELECT)).isInstanceOfSatisfying(Rejected.class,
                r -> assertThat(r.ruleId()).isEqualTo("WRONG_STATEMENT_KIND"));
        assertThat(guard.check("SELECT 1", StatementKind.INSERT)).isInstanceOfSatisfying(Rejected.class,
                r -> assertThat(r.ruleId()).isEqualTo("WRONG_STATEMENT_KIND"));
    }

    @Test
    void rejectionsFormatAsMcpToolErrors() {
        assertThat(new QueryRejectedException("RULE", "why")).hasMessage("REJECTED[RULE]: why");
    }
}
