package com.example.mcpsql.server.db;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@Testcontainers
class PostgresBootstrapIT {

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
    JdbcTemplate jdbc;

    @Test
    void connectsAsLeastPrivilegeRole() {
        assertThat(jdbc.queryForObject("SELECT current_user", String.class)).isEqualTo("mcp_app");
    }

    @Test
    void seedDataIsQueryable() {
        assertThat(jdbc.queryForObject("SELECT count(*) FROM shop.orders", Integer.class)).isEqualTo(20);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM shop.customers", Integer.class)).isEqualTo(12);
    }

    @Test
    void order2TotalMatchesBusinessRule() {
        BigDecimal total = jdbc.queryForObject("""
                SELECT round(sum(quantity * unit_price * (1 - discount_pct / 100)), 2)
                FROM shop.order_items
                WHERE order_id = 2
                """, BigDecimal.class);

        assertThat(total).isEqualByComparingTo("1272.53");
    }

    @Test
    void deleteIsDeniedByTheDatabase() {
        assertThatThrownBy(() -> jdbc.update("DELETE FROM shop.orders"))
                .rootCause()
                .hasMessageContaining("permission denied");

        assertThat(jdbc.queryForObject("SELECT count(*) FROM shop.orders", Integer.class)).isEqualTo(20);
    }

    @ParameterizedTest
    @CsvSource({
            "categories, description, category_id",
            "customers, city, customer_id",
            "employees, job_title, employee_id",
            "order_items, quantity, order_item_id",
            "orders, notes, order_id",
            "payments, amount, payment_id",
            "products, name, product_id"
    })
    void everyTableCanBeUpdated(String table, String column, String key) {
        String sql = "UPDATE shop.%s SET %s = %s WHERE %s = 1".formatted(table, column, column, key);

        assertThat(jdbc.update(sql)).isEqualTo(1);
    }

    @Test
    void updateWithoutWhereReachesEveryRow() {
        assertThat(jdbc.update("UPDATE shop.products SET name = name")).isEqualTo(16);
    }

    @Test
    void systemCatalogsAreReadable() {
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM pg_catalog.pg_class WHERE relname = 'orders'", Integer.class)).isEqualTo(1);
    }

    @Test
    void insertOutsideTheAllowedTablesIsDeniedByTheDatabase() {
        assertThatThrownBy(() -> jdbc.update("INSERT INTO shop.categories (name) VALUES ('x')"))
                .rootCause()
                .hasMessageContaining("permission denied");
    }
}
