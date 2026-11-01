package com.example.mcpsql.server.dto;

public record ColumnInfo(
        String name,
        String type,
        boolean nullable,
        String defaultValue,
        boolean primaryKey,
        String comment) {
}
