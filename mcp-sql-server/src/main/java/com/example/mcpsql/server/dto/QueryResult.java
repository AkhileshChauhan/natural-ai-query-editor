package com.example.mcpsql.server.dto;

import java.util.List;

/** {@code rows} line up with {@code columns} by position, so duplicate column names survive. */
public record QueryResult(
        List<String> columns,
        List<List<Object>> rows,
        int rowCount,
        boolean truncated,
        long elapsedMs) {
}
