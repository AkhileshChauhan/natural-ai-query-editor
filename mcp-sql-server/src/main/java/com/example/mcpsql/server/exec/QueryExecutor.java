package com.example.mcpsql.server.exec;

import com.example.mcpsql.server.config.SqlGuardProperties;
import com.example.mcpsql.server.dto.QueryResult;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Runs SQL the guard has already allowed, in a read-only transaction so the database refuses any write. */
@Component
public class QueryExecutor {

    private final JdbcTemplate jdbc;
    private final int maxRows;

    public QueryExecutor(DataSource dataSource, SqlGuardProperties properties) {
        this.jdbc = new JdbcTemplate(dataSource);
        this.jdbc.setQueryTimeout(properties.queryTimeoutSeconds());
        this.maxRows = properties.maxRows();
    }

    /** @param requestedRows lowers the row cap for this call; null uses the configured cap */
    @Transactional(readOnly = true)
    public QueryResult select(String sql, Integer requestedRows) {
        int limit = requestedRows == null ? maxRows : Math.clamp(requestedRows, 1, maxRows);
        // sql comes from the guard, free of comments and semicolons. The limit is a literal because the
        // plain Statement this needs leaves the JSON operator ? alone, where a PreparedStatement would not.
        String capped = "SELECT * FROM (\n" + sql + "\n) AS q LIMIT " + (limit + 1);
        long start = System.nanoTime();
        try {
            ResultSetExtractor<QueryResult> extractor = rs -> readResult(rs, limit, start);
            return jdbc.query(capped, extractor);
        } catch (DataAccessException e) {
            throw QueryExecutionException.from(e);
        }
    }

    private static QueryResult readResult(ResultSet rs, int limit, long start) throws SQLException {
        List<String> columns = RowReader.columns(rs);
        List<List<Object>> rows = new ArrayList<>();
        boolean truncated = false;
        while (rs.next()) {
            if (rows.size() == limit) {
                truncated = true;
                break;
            }
            rows.add(RowReader.row(rs));
        }
        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
        return new QueryResult(columns, rows, rows.size(), truncated, elapsedMs);
    }
}
