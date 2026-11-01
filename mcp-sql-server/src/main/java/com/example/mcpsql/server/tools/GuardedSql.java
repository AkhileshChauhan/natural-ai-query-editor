package com.example.mcpsql.server.tools;

import com.example.mcpsql.server.audit.AuditLogger;
import com.example.mcpsql.server.guard.GuardResult;
import com.example.mcpsql.server.guard.QueryRejectedException;
import com.example.mcpsql.server.guard.SqlGuard;
import com.example.mcpsql.server.guard.StatementKind;
import java.util.function.Function;
import java.util.function.ToIntFunction;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.stereotype.Component;

/** The one path from a SQL tool to the database: validate, execute only if allowed, audit either way. */
@Component
class GuardedSql {

    private final SqlGuard guard;
    private final AuditLogger audit;

    GuardedSql(SqlGuard guard, AuditLogger audit) {
        this.guard = guard;
        this.audit = audit;
    }

    <T> T run(ToolContext context, String tool, String sql, StatementKind kind, Boolean dryRun,
            Function<String, T> execute, ToIntFunction<T> rowCount) {
        GuardResult result = guard.check(sql, kind);
        String logged = result instanceof GuardResult.Allowed allowed ? allowed.sql() : sql;
        return audit.audited(context, tool, logged, dryRun, () -> switch (result) {
            case GuardResult.Allowed allowed -> execute.apply(allowed.sql());
            case GuardResult.Rejected rejected -> throw new QueryRejectedException(rejected.ruleId(), rejected.reason());
        }, rowCount);
    }
}
