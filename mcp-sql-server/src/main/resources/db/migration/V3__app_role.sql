-- =====================================================================
-- V3__app_role.sql
-- Least-privilege login used by the MCP server at runtime.
-- ${mcpAppPassword} and ${dbName} are Flyway placeholders supplied by
-- EmbeddedPostgresConfig (never hard-code the password).
-- PostgreSQL denies anything not granted, so there is no DENY:
-- simply never GRANT DELETE, TRUNCATE, or DDL.
-- =====================================================================

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'mcp_app') THEN
        CREATE ROLE mcp_app LOGIN;
    END IF;
END
$$;

ALTER ROLE mcp_app WITH LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION
    PASSWORD '${mcpAppPassword}';

-- Server-side safety nets applied to every mcp_app session
ALTER ROLE mcp_app SET statement_timeout = '30s';
ALTER ROLE mcp_app SET idle_in_transaction_session_timeout = '60s';
ALTER ROLE mcp_app SET search_path = shop;

-- Lock down defaults
REVOKE ALL ON DATABASE ${dbName} FROM PUBLIC;
GRANT  CONNECT ON DATABASE ${dbName} TO mcp_app;
REVOKE CREATE ON SCHEMA public FROM PUBLIC;
REVOKE ALL ON SCHEMA shop FROM PUBLIC;

-- Read access to the whole shop schema
GRANT USAGE  ON SCHEMA shop TO mcp_app;
GRANT SELECT ON ALL TABLES IN SCHEMA shop TO mcp_app;

-- Write access (INSERT/UPDATE only) to the write allow-listed tables
GRANT INSERT, UPDATE ON shop.customers, shop.orders, shop.order_items, shop.products TO mcp_app;
GRANT USAGE ON ALL SEQUENCES IN SCHEMA shop TO mcp_app;   -- needed for identity columns on INSERT

-- Explicitly make sure nothing destructive is granted
REVOKE DELETE, TRUNCATE, REFERENCES, TRIGGER ON ALL TABLES IN SCHEMA shop FROM mcp_app;
