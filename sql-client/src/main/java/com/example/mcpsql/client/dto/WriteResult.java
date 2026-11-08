package com.example.mcpsql.client.dto;

/** The {@code run_insert} and {@code run_update} result. {@code returning} is null without a RETURNING clause. */
public record WriteResult(int affectedRows, boolean committed, boolean dryRun, SelectResult returning) {
}
