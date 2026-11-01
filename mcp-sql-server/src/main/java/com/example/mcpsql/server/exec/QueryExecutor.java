package com.example.mcpsql.server.exec;

import com.example.mcpsql.server.config.SqlGuardProperties;
import com.example.mcpsql.server.dto.QueryResult;
import java.math.BigDecimal;
import java.sql.Array;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Time;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Base64;
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
        ResultSetMetaData meta = rs.getMetaData();
        List<String> columns = new ArrayList<>();
        for (int i = 1; i <= meta.getColumnCount(); i++) {
            columns.add(meta.getColumnLabel(i));
        }
        List<List<Object>> rows = new ArrayList<>();
        boolean truncated = false;
        while (rs.next()) {
            if (rows.size() == limit) {
                truncated = true;
                break;
            }
            List<Object> row = new ArrayList<>(columns.size());
            for (int i = 1; i <= columns.size(); i++) {
                row.add(toJsonValue(rs.getObject(i), meta.getColumnTypeName(i)));
            }
            rows.add(row);
        }
        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
        return new QueryResult(columns, rows, rows.size(), truncated, elapsedMs);
    }

    /** Strings for values JSON numbers would distort, ISO-8601 for temporals, base64 for bytea. */
    private static Object toJsonValue(Object value, String typeName) throws SQLException {
        return switch (value) {
            case null -> null;
            case Boolean b -> b;
            case Integer i -> i;
            case Long l -> l;
            case Short s -> s;
            case String s -> s;
            case BigDecimal d -> d.toPlainString();
            case Timestamp t when typeName.endsWith("timestamptz") -> t.toInstant().toString();
            case Timestamp t -> t.toLocalDateTime().toString();
            case Date d -> d.toLocalDate().toString();
            case Time t -> t.toLocalTime().toString();
            case byte[] bytes -> Base64.getEncoder().encodeToString(bytes);
            case Array array -> toJsonValue(array.getArray(), typeName);
            case Object[] items -> {
                List<Object> list = new ArrayList<>(items.length);
                for (Object item : items) {
                    list.add(toJsonValue(item, typeName));
                }
                yield list;
            }
            default -> value.toString();
        };
    }
}
