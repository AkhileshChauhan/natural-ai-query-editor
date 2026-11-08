package com.example.mcpsql.client.dto;

/** {@code ruleId} is set only for guard rejections. */
public record ApiError(String code, String ruleId, String message) {
}
