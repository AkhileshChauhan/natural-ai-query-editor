package com.example.mcpsql.server.guard;

/** Outcome of validating one SQL string. */
public sealed interface GuardResult {

    /** {@code sql} has comments and the trailing semicolon removed, so it is safe to wrap in a subquery. */
    record Allowed(StatementKind kind, String sql) implements GuardResult {
    }

    record Rejected(String ruleId, String reason) implements GuardResult {
    }
}
