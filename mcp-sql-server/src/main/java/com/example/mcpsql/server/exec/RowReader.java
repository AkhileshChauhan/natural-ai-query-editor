package com.example.mcpsql.server.exec;

import java.math.BigDecimal;
import java.sql.Array;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Time;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/** Converts result rows to the JSON shapes the tool contract promises. */
final class RowReader {

    private RowReader() {
    }

    static List<String> columns(ResultSet rs) throws SQLException {
        ResultSetMetaData meta = rs.getMetaData();
        List<String> columns = new ArrayList<>(meta.getColumnCount());
        for (int i = 1; i <= meta.getColumnCount(); i++) {
            columns.add(meta.getColumnLabel(i));
        }
        return columns;
    }

    static List<Object> row(ResultSet rs) throws SQLException {
        ResultSetMetaData meta = rs.getMetaData();
        List<Object> row = new ArrayList<>(meta.getColumnCount());
        for (int i = 1; i <= meta.getColumnCount(); i++) {
            row.add(toJsonValue(rs.getObject(i), meta.getColumnTypeName(i)));
        }
        return row;
    }

    /** Strings for values JSON numbers would distort, ISO-8601 for temporals, base64 for bytea. */
    private static Object toJsonValue(Object value, String typeName) throws SQLException {
        return switch (value) {
            case null -> null;
            case Boolean b -> b;
            case Integer i -> i;
            case Long l -> l;
            case Short s -> s;
            case String s -> s;
            case BigDecimal d -> d.toPlainString();
            case Timestamp t when typeName.endsWith("timestamptz") -> t.toInstant().toString();
            case Timestamp t -> t.toLocalDateTime().toString();
            case Date d -> d.toLocalDate().toString();
            case Time t -> t.toLocalTime().toString();
            case byte[] bytes -> Base64.getEncoder().encodeToString(bytes);
            case Array array -> toJsonValue(array.getArray(), typeName);
            case Object[] items -> {
                List<Object> list = new ArrayList<>(items.length);
                for (Object item : items) {
                    list.add(toJsonValue(item, typeName));
                }
                yield list;
            }
            default -> value.toString();
        };
    }
}
