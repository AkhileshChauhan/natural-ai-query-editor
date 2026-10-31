package com.example.mcpsql.server.db;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class DbPropertiesTest {

    private static DbProperties withUrl(String url) {
        return new DbProperties(url, "postgres", "postgres", "mcp_app", "secret");
    }

    @Test
    void parsesDatabaseNameFromUrl() {
        assertThat(withUrl("jdbc:postgresql://localhost:5432/shopdb").databaseName()).isEqualTo("shopdb");
    }

    @Test
    void ignoresQueryParameters() {
        assertThat(withUrl("jdbc:postgresql://localhost:5432/shop_db2?ssl=false").databaseName())
                .isEqualTo("shop_db2");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "jdbc:postgresql://localhost:5432/",
            "jdbc:postgresql://localhost:5432",
            "jdbc:postgresql://localhost:5432/Shop",
            "jdbc:postgresql://localhost:5432/2shop",
            "jdbc:postgresql://localhost:5432/shop%22db",
            "jdbc:postgresql:shopdb",
            "postgresql://localhost:5432/shopdb"
    })
    void rejectsUnsafeOrMissingDatabaseName(String url) {
        assertThatThrownBy(() -> withUrl(url).databaseName()).isInstanceOf(IllegalArgumentException.class);
    }
}
