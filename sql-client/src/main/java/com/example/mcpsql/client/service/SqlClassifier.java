package com.example.mcpsql.client.service;

import com.example.mcpsql.client.dto.StatementKind;
import java.util.Optional;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statements;
import net.sf.jsqlparser.statement.insert.Insert;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.update.Update;

/**
 * Works out which tool a statement belongs to, which a first keyword cannot do for {@code WITH ... UPDATE}.
 * This only routes SQL. Allowing it is the server's job, and empty means nothing more than "not recognised".
 */
final class SqlClassifier {

    private SqlClassifier() {
    }

    static Optional<StatementKind> classify(String sql) {
        Statements statements;
        try {
            statements = CCJSqlParserUtil.parseStatements(sql);
        } catch (JSQLParserException e) {
            return Optional.empty();
        }
        if (statements.size() != 1) {
            return Optional.empty();
        }
        return switch (statements.getFirst()) {
            case Select ignored -> Optional.of(StatementKind.SELECT);
            case Insert ignored -> Optional.of(StatementKind.INSERT);
            case Update ignored -> Optional.of(StatementKind.UPDATE);
            default -> Optional.empty();
        };
    }
}
