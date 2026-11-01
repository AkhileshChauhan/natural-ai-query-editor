package com.example.mcpsql.server.dto;

/** {@code rowEstimate} is null until PostgreSQL has analyzed the table. */
public record TableInfo(String schema, String table, Long rowEstimate, String comment) {
}
