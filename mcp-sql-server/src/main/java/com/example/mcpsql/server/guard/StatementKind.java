package com.example.mcpsql.server.guard;

/** The only statement kinds the guard ever allows. */
public enum StatementKind {
    SELECT,
    INSERT,
    UPDATE
}
