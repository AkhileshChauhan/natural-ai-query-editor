package com.example.mcpsql.server.exec;

import com.example.mcpsql.server.config.SqlGuardProperties;
import com.example.mcpsql.server.dto.QueryResult;
import com.example.mcpsql.server.dto.WriteResult;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.springframework.core.NestedRuntimeException;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.StatementCallback;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.support.TransactionTemplate;

/** Runs SQL the guard has already allowed. A dry run really executes the statement, then rolls it back. */
@Component
public class WriteExecutor {

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    private final int maxReturnedRows;

    public WriteExecutor(DataSource dataSource, PlatformTransactionManager transactionManager, SqlGuardProperties properties) {
        this.jdbc = new JdbcTemplate(dataSource);
        this.jdbc.setQueryTimeout(properties.queryTimeoutSeconds());
        this.transaction = new TransactionTemplate(transactionManager);
        this.maxReturnedRows = properties.maxRows();
    }

    public WriteResult execute(String sql, boolean dryRun) {
        long start = System.nanoTime();
        StatementCallback<WriteResult> write = statement -> run(statement, sql, dryRun, start);
        try {
            return transaction.execute(status -> {
                if (dryRun) {
                    status.setRollbackOnly();
                }
                return jdbc.execute(write);
            });
        } catch (DataAccessException | TransactionException e) {
            throw QueryExecutionException.from((NestedRuntimeException) e);
        }
    }

    private WriteResult run(Statement statement, String sql, boolean dryRun, long start) throws SQLException {
        if (!statement.execute(sql)) {
            return new WriteResult(statement.getUpdateCount(), !dryRun, dryRun, null);
        }
        try (ResultSet rs = statement.getResultSet()) {
            return readReturning(rs, dryRun, start);
        }
    }

    /** RETURNING yields one row per affected row. All are counted, but only some are sent back. */
    private WriteResult readReturning(ResultSet rs, boolean dryRun, long start) throws SQLException {
        List<String> columns = RowReader.columns(rs);
        List<List<Object>> rows = new ArrayList<>();
        int affected = 0;
        while (rs.next()) {
            affected++;
            if (rows.size() < maxReturnedRows) {
                rows.add(RowReader.row(rs));
            }
        }
        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
        QueryResult returning = new QueryResult(columns, rows, rows.size(), affected > rows.size(), elapsedMs);
        return new WriteResult(affected, !dryRun, dryRun, returning);
    }
}
