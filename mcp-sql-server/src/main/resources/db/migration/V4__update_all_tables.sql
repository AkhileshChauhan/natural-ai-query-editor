-- =====================================================================
-- V4__update_all_tables.sql
-- UPDATE is allowed on every shop table. INSERT stays on the tables
-- granted in V3. DELETE, TRUNCATE and DDL are still never granted.
-- =====================================================================

GRANT UPDATE ON ALL TABLES IN SCHEMA shop TO mcp_app;

-- Tables added by later migrations are readable and updatable too
ALTER DEFAULT PRIVILEGES IN SCHEMA shop GRANT SELECT, UPDATE ON TABLES TO mcp_app;
