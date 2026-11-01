package com.example.mcpsql.client.dto;

import java.util.List;

/** The {@code run_select} result. {@code rows} line up with {@code columns} by position. */
public record SelectResult(
        List<String> columns,
        List<List<Object>> rows,
        int rowCount,
        boolean truncated,
        long elapsedMs) {
}
