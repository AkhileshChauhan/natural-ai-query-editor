package com.example.mcpsql.client.dto;

public record TableInfo(String schema, String table, Long rowEstimate, String comment) {
}
