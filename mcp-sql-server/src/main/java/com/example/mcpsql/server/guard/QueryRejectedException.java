package com.example.mcpsql.server.guard;

/** A guard rule refused the SQL. The message is the MCP tool error text. */
public class QueryRejectedException extends RuntimeException {

    private final String ruleId;
    private final String reason;

    public QueryRejectedException(String ruleId, String reason) {
        super("REJECTED[" + ruleId + "]: " + reason);
        this.ruleId = ruleId;
        this.reason = reason;
    }

    public String ruleId() {
        return ruleId;
    }

    public String reason() {
        return reason;
    }
}
