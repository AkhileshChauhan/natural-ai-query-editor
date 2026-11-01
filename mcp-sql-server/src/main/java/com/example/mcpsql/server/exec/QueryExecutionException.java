package com.example.mcpsql.server.exec;

import java.sql.SQLException;
import org.springframework.core.NestedRuntimeException;

/** The database refused or cancelled a statement. The message is the MCP tool error text. */
public class QueryExecutionException extends RuntimeException {

    private static final String QUERY_CANCELED = "57014";
    private static final String TIMEOUT = "TIMEOUT";
    private static final String DB_ERROR = "DB_ERROR";

    private final String code;

    private QueryExecutionException(String code, String message) {
        super(code + ": " + message);
        this.code = code;
    }

    public String code() {
        return code;
    }

    /** Keeps only PostgreSQL's one-line message, so the failing SQL and stack trace stay on the server. */
    static QueryExecutionException from(NestedRuntimeException e) {
        if (e.getMostSpecificCause() instanceof SQLException sql) {
            if (QUERY_CANCELED.equals(sql.getSQLState())) {
                return new QueryExecutionException(TIMEOUT, "The statement took too long and was cancelled");
            }
            if (sql.getMessage() != null) {
                return new QueryExecutionException(DB_ERROR, sql.getMessage().lines().findFirst().orElse(""));
            }
        }
        return new QueryExecutionException(DB_ERROR, "The statement could not be executed");
    }
}
