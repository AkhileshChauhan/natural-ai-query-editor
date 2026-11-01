package com.example.mcpsql.server.guard;

import com.example.mcpsql.server.guard.GuardResult.Allowed;
import com.example.mcpsql.server.guard.GuardResult.Rejected;
import java.util.Collection;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.Statements;
import net.sf.jsqlparser.statement.insert.ConflictActionType;
import net.sf.jsqlparser.statement.insert.Insert;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.update.Update;

/**
 * Decides whether a SQL string may run. SELECT and UPDATE are unrestricted, so the guard's job is to
 * make sure the text really is one such statement and not a blocked statement in disguise.
 * Anything it cannot positively validate is rejected.
 */
public final class SqlGuard {

    /** Long enough for any real query; the parser needs seconds on pathological input well above this. */
    private static final int MAX_SQL_LENGTH = 20_000;

    /** Matches the {@code search_path} of the {@code mcp_app} role. */
    private static final String DEFAULT_SCHEMA = "shop";

    private final Set<String> insertAllowedTables;

    /** @param insertAllowedTables tables as {@code schema.table} */
    public SqlGuard(Collection<String> insertAllowedTables) {
        this.insertAllowedTables = insertAllowedTables.stream()
                .map(table -> table.toLowerCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
    }

    /** Validates {@code sql} for a tool that accepts only {@code expected} statements. */
    public GuardResult check(String sql, StatementKind expected) {
        GuardResult result = check(sql);
        if (result instanceof Allowed allowed && allowed.kind() != expected) {
            return new Rejected("WRONG_STATEMENT_KIND",
                    "Expected a " + expected + " statement but got " + allowed.kind());
        }
        return result;
    }

    public GuardResult check(String sql) {
        try {
            return validate(sql);
        } catch (QueryRejectedException e) {
            return new Rejected(e.ruleId(), e.reason());
        } catch (RuntimeException | StackOverflowError e) {
            return new Rejected("GUARD_ERROR", "The statement could not be validated");
        }
    }

    private GuardResult validate(String sql) {
        if (sql == null || sql.isBlank()) {
            throw new QueryRejectedException("EMPTY_SQL", "SQL is empty");
        }
        if (sql.length() > MAX_SQL_LENGTH) {
            throw new QueryRejectedException("SQL_TOO_LONG", "SQL exceeds " + MAX_SQL_LENGTH + " characters");
        }
        String text = SqlScanner.scan(sql);
        Statement statement = parseSingle(text);
        StatementKind kind = switch (statement) {
            case Select ignored -> StatementKind.SELECT;
            case Insert ignored -> StatementKind.INSERT;
            case Update ignored -> StatementKind.UPDATE;
            default -> throw SqlScanner.notPermitted(statement.getClass().getSimpleName());
        };
        BlockedNodeVisitor.check(statement);
        if (statement instanceof Insert insert) {
            checkInsert(insert);
        }
        return new Allowed(kind, text);
    }

    private static Statement parseSingle(String sql) {
        Statements statements;
        try {
            statements = CCJSqlParserUtil.parseStatements(sql);
        } catch (JSQLParserException e) {
            throw new QueryRejectedException("PARSE_ERROR", "The statement could not be parsed as supported SQL");
        }
        if (statements.size() != 1) {
            throw new QueryRejectedException("MULTIPLE_STATEMENTS", "Only one statement is allowed per call");
        }
        return statements.getFirst();
    }

    private void checkInsert(Insert insert) {
        String table = insert.getTable() == null ? "" : qualifiedName(insert.getTable());
        if (!insertAllowedTables.contains(table)) {
            throw new QueryRejectedException("INSERT_TABLE_NOT_ALLOWED", "INSERT into " + table + " is not permitted");
        }
        if (insert.getConflictAction() != null
                && insert.getConflictAction().getConflictActionType() != ConflictActionType.DO_NOTHING) {
            throw new QueryRejectedException("ON_CONFLICT_UPDATE", "Only ON CONFLICT DO NOTHING is permitted");
        }
    }

    private static String qualifiedName(Table table) {
        String schema = table.getSchemaName() == null ? DEFAULT_SCHEMA : normalize(table.getSchemaName());
        return schema + "." + normalize(table.getName());
    }

    private static String normalize(String identifier) {
        return identifier.replace("\"", "").toLowerCase(Locale.ROOT);
    }
}
