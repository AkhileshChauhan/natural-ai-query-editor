package com.example.mcpsql.client.service;

import com.example.mcpsql.client.dto.SelectResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Proves the client reaches the server end to end. Runs only with {@code app.smoke-select=true}. */
@Component
@ConditionalOnProperty(name = "app.smoke-select", havingValue = "true")
class SmokeSelectRunner implements ApplicationRunner {

    private static final Logger LOG = LoggerFactory.getLogger(SmokeSelectRunner.class);
    private static final String SQL = "SELECT status, count(*) AS orders FROM shop.orders GROUP BY status ORDER BY status";

    private final SqlExecutionService execution;

    SmokeSelectRunner(SqlExecutionService execution) {
        this.execution = execution;
    }

    @Override
    public void run(ApplicationArguments args) {
        SelectResult result = execution.select(SQL);
        LOG.info("Smoke SELECT returned {} rows: {} {}", result.rowCount(), result.columns(), result.rows());
    }
}
