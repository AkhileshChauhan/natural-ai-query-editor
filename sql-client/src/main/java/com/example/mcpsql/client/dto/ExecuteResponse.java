package com.example.mcpsql.client.dto;

/** Either {@code select}, or a dry-run {@code write} with the token that confirms it. */
public record ExecuteResponse(StatementKind kind, SelectResult select, WriteResult write, String previewToken) {

    public static ExecuteResponse ofSelect(SelectResult select) {
        return new ExecuteResponse(StatementKind.SELECT, select, null, null);
    }

    public static ExecuteResponse ofPreview(StatementKind kind, WriteResult write, String previewToken) {
        return new ExecuteResponse(kind, null, write, previewToken);
    }
}
