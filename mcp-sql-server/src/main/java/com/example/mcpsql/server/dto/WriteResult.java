package com.example.mcpsql.server.dto;

/** {@code returning} is null unless the statement has a RETURNING clause. */
public record WriteResult(int affectedRows, boolean committed, boolean dryRun, QueryResult returning) {
}
