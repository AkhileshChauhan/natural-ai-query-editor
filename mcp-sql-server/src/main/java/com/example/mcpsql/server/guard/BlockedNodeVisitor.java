package com.example.mcpsql.server.guard;

import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;

/**
 * Finds blocked statements hidden inside an allowed one. Walks every field of the AST reflectively
 * instead of using JSqlParser's visitors, whose coverage of node types has gaps: a node this class has
 * never heard of is still descended into.
 */
final class BlockedNodeVisitor {

    private static final String AST_PACKAGE = "net.sf.jsqlparser.";
    private static final String PARSER_PACKAGE = "net.sf.jsqlparser.parser.";

    private static final ClassValue<List<Field>> AST_FIELDS = new ClassValue<>() {
        @Override
        protected List<Field> computeValue(Class<?> type) {
            List<Field> fields = new ArrayList<>();
            for (Class<?> c = type; isAstClass(c); c = c.getSuperclass()) {
                for (Field field : c.getDeclaredFields()) {
                    if (!Modifier.isStatic(field.getModifiers()) && !field.getType().isPrimitive()) {
                        fields.add(field);
                    }
                }
            }
            AccessibleObject.setAccessible(fields.toArray(new Field[0]), true);
            return List.copyOf(fields);
        }
    };

    private BlockedNodeVisitor() {
    }

    static void check(Statement root) {
        Set<Object> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        Deque<Object> pending = new ArrayDeque<>();
        pending.push(root);
        while (!pending.isEmpty()) {
            Object node = pending.pop();
            if (!seen.add(node)) {
                continue;
            }
            if (isAstClass(node.getClass()) && !node.getClass().isEnum()) {
                visit(node, root);
                pushFields(node, pending);
            }
            if (node instanceof Iterable<?> items) {
                items.forEach(item -> pushIfPresent(item, pending));
            } else if (node instanceof Map<?, ?> map) {
                map.keySet().forEach(item -> pushIfPresent(item, pending));
                map.values().forEach(item -> pushIfPresent(item, pending));
            } else if (node instanceof Object[] items) {
                for (Object item : items) {
                    pushIfPresent(item, pending);
                }
            }
        }
    }

    private static void pushFields(Object node, Deque<Object> pending) {
        for (Field field : AST_FIELDS.get(node.getClass())) {
            try {
                pushIfPresent(field.get(node), pending);
            } catch (IllegalAccessException e) {
                throw new IllegalStateException("Cannot inspect " + field, e);
            }
        }
    }

    private static void pushIfPresent(Object value, Deque<Object> pending) {
        if (value != null) {
            pending.push(value);
        }
    }

    private static boolean isAstClass(Class<?> type) {
        return type != null && type.getName().startsWith(AST_PACKAGE) && !type.getName().startsWith(PARSER_PACKAGE);
    }

    private static void visit(Object node, Statement root) {
        if (node instanceof Statement && !(node instanceof Select) && node != root) {
            throw new QueryRejectedException("DATA_MODIFYING_CTE",
                    "Nested DELETE, INSERT or UPDATE statements are not permitted");
        }
        if (node instanceof PlainSelect select && (select.getIntoTables() != null || select.getIntoTempTable() != null)) {
            throw new QueryRejectedException("SELECT_INTO", "SELECT ... INTO is not permitted");
        }
    }
}
