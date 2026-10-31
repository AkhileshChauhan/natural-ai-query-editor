package com.example.mcpsql.server.db;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.util.Map;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.output.MigrateResult;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

@Configuration
@EnableConfigurationProperties(DbProperties.class)
class PostgresBootstrapConfig {

    /** Runs schema + seed + role scripts as admin. The admin DataSource is NOT a bean. */
    @Bean
    MigrateResult dbBootstrap(DbProperties p) {
        var admin = new DriverManagerDataSource(p.url(), p.adminUser(), p.adminPassword());
        try {
            var result = Flyway.configure()
                    .dataSource(admin)
                    .locations("classpath:db/migration")
                    .placeholders(Map.of("mcpAppPassword", p.appPassword(), "dbName", p.databaseName()))
                    .load()
                    .migrate();
            var jdbc = new JdbcTemplate(admin);
            String ddl = jdbc.queryForObject(
                    "SELECT format('ALTER ROLE mcp_app PASSWORD %L', ?)", String.class, p.appPassword());
            jdbc.execute(ddl);                     // keep password in sync with config
            return result;
        } catch (CannotGetJdbcConnectionException | FlywayException e) {
            throw new IllegalStateException("PostgreSQL not reachable at " + p.url()
                    + " — did you run `docker compose up -d`?", e);
        }
    }

    /** The ONLY DataSource the app uses: least-privilege mcp_app. */
    @Bean
    @DependsOn("dbBootstrap")
    DataSource dataSource(DbProperties p) {
        var cfg = new HikariConfig();
        cfg.setJdbcUrl(p.url());
        cfg.setUsername(p.appUser());
        cfg.setPassword(p.appPassword());
        cfg.setMaximumPoolSize(10);
        cfg.setPoolName("mcp-app");
        return new HikariDataSource(cfg);
    }
}
