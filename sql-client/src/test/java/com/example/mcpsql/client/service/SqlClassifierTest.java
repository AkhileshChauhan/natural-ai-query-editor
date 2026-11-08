package com.example.mcpsql.client.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.mcpsql.client.dto.StatementKind;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class SqlClassifierTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
            SELECT 1                                                                | SELECT
            select 1;                                                               | SELECT
            (SELECT 1)                                                              | SELECT
            WITH a AS (SELECT 1) SELECT * FROM a                                    | SELECT
            /* note */ SELECT 1 -- end                                              | SELECT
            INSERT INTO shop.orders (customer_id) VALUES (1)                        | INSERT
            WITH a AS (SELECT 1 AS id) INSERT INTO shop.orders (customer_id) SELECT id FROM a | INSERT
            UPDATE shop.products SET stock_qty = 1                                  | UPDATE
            WITH a AS (SELECT 1 AS id) UPDATE shop.products p SET stock_qty = 1 FROM a WHERE p.product_id = a.id | UPDATE
            """)
    void recognisesEachKind(String sql, StatementKind kind) {
        assertThat(SqlClassifier.classify(sql)).contains(kind);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "DELETE FROM shop.orders", "DROP TABLE x", "SELECT 1; SELECT 2", "not sql"})
    void recognisesNothingElse(String sql) {
        assertThat(SqlClassifier.classify(sql)).isEmpty();
    }
}
