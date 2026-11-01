package com.example.mcpsql.server.guard;

import java.util.Locale;
import java.util.Set;

/**
 * Reads SQL the way PostgreSQL does, independently of JSqlParser, so the two cannot be played off against
 * each other to hide a blocked statement. Removes comments and the trailing semicolon, which would
 * otherwise break the row-cap wrapper, and refuses literal forms whose boundaries JSqlParser may misjudge.
 */
final class SqlScanner {

    private static final Set<String> ALLOWED_FIRST_TOKENS = Set.of("select", "with", "values", "table", "insert", "update", "(");
    private static final int MAX_ECHOED_KEYWORD = 20;

    private final String sql;
    private final StringBuilder text = new StringBuilder();
    private boolean anyToken;
    private int pos;

    private SqlScanner(String sql) {
        this.sql = sql;
    }

    /** Returns the SQL without comments or the trailing semicolon. */
    static String scan(String sql) {
        return new SqlScanner(sql).run();
    }

    private String run() {
        boolean terminated = false;
        while (pos < sql.length()) {
            char c = sql.charAt(pos);
            if (isSpace(c)) {
                text.append(c);
                pos++;
            } else if (sql.startsWith("--", pos)) {
                skipLineComment();
            } else if (sql.startsWith("/*", pos)) {
                skipBlockComment();
            } else if (terminated) {
                throw new QueryRejectedException("MULTIPLE_STATEMENTS", "Only one statement is allowed per call");
            } else if (c == ';') {
                terminated = true;
                pos++;
            } else {
                scanToken(c);
            }
        }
        if (!anyToken) {
            throw new QueryRejectedException("EMPTY_SQL", "SQL is empty");
        }
        return text.toString().strip();
    }

    /** Checks the first token as soon as it is read, so {@code DO $$...$$} is reported as DO, not as a dollar quote. */
    private void onToken(String token) {
        if (!anyToken && !ALLOWED_FIRST_TOKENS.contains(token.toLowerCase(Locale.ROOT))) {
            throw notPermitted(token);
        }
        anyToken = true;
    }

    static QueryRejectedException notPermitted(String keyword) {
        String reason = keyword.length() > MAX_ECHOED_KEYWORD
                ? "This statement is"
                : keyword.toUpperCase(Locale.ROOT) + " statements are";
        return new QueryRejectedException("STATEMENT_NOT_ALLOWED", reason + " not permitted");
    }

    private void scanToken(char c) {
        if (c == '\'') {
            scanQuoted(false);
        } else if (c == '"') {
            scanQuotedIdentifier();
        } else if (c == '$') {
            throw new QueryRejectedException("UNSAFE_LITERAL", "Dollar quoting and $n parameters are not supported");
        } else if (isWordStart(c)) {
            scanWord();
        } else if (c < ' ' || c > '~') {
            throw new QueryRejectedException("INVALID_CHARACTER",
                    "Non-ASCII and control characters are only allowed inside string literals");
        } else {
            onToken(String.valueOf(c));
            text.append(c);
            pos++;
        }
    }

    private void scanWord() {
        int start = pos;
        while (pos < sql.length() && isWordPart(sql.charAt(pos))) {
            pos++;
        }
        String word = sql.substring(start, pos);
        onToken(word);
        text.append(word);
        if (word.equalsIgnoreCase("u") && sql.startsWith("&", pos) && isQuote(pos + 1)) {
            throw new QueryRejectedException("UNSAFE_LITERAL", "Unicode-escaped strings and identifiers are not supported");
        }
        if (word.equalsIgnoreCase("e") && isQuote(pos) && sql.charAt(pos) == '\'') {
            scanQuoted(true);
        }
    }

    private void scanQuoted(boolean rejectBackslash) {
        int start = pos;
        pos = endOfQuoted('\'');
        String literal = sql.substring(start, pos);
        if (rejectBackslash && literal.indexOf('\\') >= 0) {
            throw new QueryRejectedException("UNSAFE_LITERAL", "Backslash escapes in E'' strings are not supported");
        }
        onToken(literal);
        text.append(literal);
    }

    private void scanQuotedIdentifier() {
        int start = pos;
        pos = endOfQuoted('"');
        String quoted = sql.substring(start, pos);
        onToken(quoted.substring(1, quoted.length() - 1));
        text.append(quoted);
    }

    private int endOfQuoted(char quote) {
        int i = pos + 1;
        while (i < sql.length()) {
            if (sql.charAt(i) != quote) {
                i++;
            } else if (i + 1 < sql.length() && sql.charAt(i + 1) == quote) {
                i += 2;
            } else {
                return i + 1;
            }
        }
        throw new QueryRejectedException("UNTERMINATED_TOKEN", "Unterminated string or quoted identifier");
    }

    private void skipLineComment() {
        while (pos < sql.length() && sql.charAt(pos) != '\n' && sql.charAt(pos) != '\r') {
            pos++;
        }
        text.append(' ');
    }

    /** PostgreSQL block comments nest, unlike most SQL dialects. */
    private void skipBlockComment() {
        int depth = 0;
        do {
            if (sql.startsWith("/*", pos)) {
                depth++;
                pos += 2;
            } else if (sql.startsWith("*/", pos)) {
                depth--;
                pos += 2;
            } else if (pos < sql.length()) {
                pos++;
            } else {
                throw new QueryRejectedException("UNTERMINATED_TOKEN", "Unterminated block comment");
            }
        } while (depth > 0);
        text.append(' ');
    }

    private boolean isQuote(int index) {
        return index < sql.length() && (sql.charAt(index) == '\'' || sql.charAt(index) == '"');
    }

    private static boolean isSpace(char c) {
        return c == ' ' || c == '\t' || c == '\n' || c == '\r' || c == '\f';
    }

    private static boolean isWordStart(char c) {
        return c == '_' || (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
    }

    private static boolean isWordPart(char c) {
        return isWordStart(c) || c == '$' || (c >= '0' && c <= '9');
    }
}
