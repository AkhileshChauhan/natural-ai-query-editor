package com.example.mcpsql.server.config;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Guard and execution limits. */
@Validated
@ConfigurationProperties("sqlguard")
public record SqlGuardProperties(
        @Positive int maxRows,
        @Positive int queryTimeoutSeconds,
        @NotNull List<String> insertAllowedTables) {
}
