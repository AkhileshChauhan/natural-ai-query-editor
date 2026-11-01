package com.example.mcpsql.server.dto;

import java.util.List;

public record ForeignKeyInfo(String name, List<String> columns, String referencedTable, List<String> referencedColumns) {
}
