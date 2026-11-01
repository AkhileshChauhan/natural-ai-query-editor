package com.example.mcpsql.server.dto;

import java.util.List;

public record TableDetail(
        String schema,
        String table,
        String comment,
        List<ColumnInfo> columns,
        List<ForeignKeyInfo> foreignKeys,
        List<IndexInfo> indexes) {
}
