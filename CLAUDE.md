# CLAUDE.md — natural-ai-query-editor: SQL MCP Server + LLM Web Client (Spring Boot / Spring AI / PostgreSQL)

AI context for this repository. Read this before writing or changing code.

## 1. Project Goal

Build two Spring Boot applications:

1. **`mcp-sql-server`**: an MCP (Model Context Protocol) server built with **Spring AI MCP Server**, in front of a **PostgreSQL** database. PostgreSQL runs locally in a **Docker container** (`docker-compose.yml` at the repo root). On its first start the server creates the `shop` schema, loads sample data and creates a least-privilege role (§4). The database stays up on its own, so developers can also connect to it directly with psql, pgAdmin, DBeaver or IntelliJ. It exposes tools to:
   - inspect the schema (tables, columns, foreign keys)
   - run `SELECT` queries, including complex reads: multi-table `JOIN`s, self-joins, CTEs, aggregates, window functions, subqueries
   - run `INSERT` statements
   - run `UPDATE` statements on any table, with or without a `WHERE` clause

   It must **reject destructive or administrative SQL**: `DELETE`, `TRUNCATE`, `DROP`, `ALTER`, `MERGE`, `COPY`, `CALL`, `DO` and others (full list in §5).

2. **`sql-client`**: a Spring Boot **web application** built with **Spring AI ChatClient + Spring AI MCP Client**. On its web page the user:
   1. types a request in plain English (e.g. *"Top 5 customers by total spend in 2025"*)
   2. gets back the **native PostgreSQL query** that the LLM generated, with a short explanation, shown in an editable box
   3. clicks **Run**. The client sends that SQL to the MCP server
   4. sees the outcome on the same page:
      - **SELECT**: results rendered as an HTML table (columns, rows, row count, "truncated" notice)
      - **UPDATE / INSERT**: a preview of the affected row count first (dry run), then **Confirm**, then the final affected row count
      - **Rejected**: the server's reason (e.g. "DELETE statements are not permitted")

```
┌─────────────────────────── sql-client (Spring Boot, :8080) ───────────────────────────────┐
│  Browser ──(1) question──▶ QueryController ──▶ SqlGenerationService ──▶ ChatClient ──▶ LLM │
│     ▲                                              │  (MCP schema tools as ToolCallbacks)  │
│     │◀─(2) native SQL + explanation ───────────────┘                                       │
│     ├──(3) Run / Confirm ─▶ QueryController ──▶ SqlExecutionService ──▶ McpSyncClient ─────┼──┐
│     │◀─(4) result table / affected rows / rejection ◀──────────────────────────────────────┤  │
└────────────────────────────────────────────────────────────────────────────────────────────┘  │
                                                                              MCP (Streamable HTTP / SSE)
┌─────────────────────────── mcp-sql-server (Spring Boot, :8081) ───────────────────────────┐  │
│  @Tool methods ──▶ SqlGuard (JSqlParser) ──▶ JdbcTemplate (as mcp_app) ──┐                 │◀─┘
│                                                                          ▼                 │
│  On startup: PostgresBootstrapConfig ──▶ Flyway (as postgres): V1 schema, V2 seed, V3 role │
│                                       └─▶ app DataSource as mcp_app                        │
└────────────────────────────────────────────┬───────────────────────────────────────────────┘
                                             ▼ JDBC localhost:5432
┌──────────── Docker container naqe-postgres (postgres:16-alpine, volume pgdata) ────────────┐
│  database shopdb  ◀── also reachable from psql / pgAdmin / DBeaver / IntelliJ              │
└────────────────────────────────────────────────────────────────────────────────────────────┘
```

**Key design decision:** the LLM **only generates SQL**. It never executes anything on its own. Execution happens when the user clicks Run, and the client calls the MCP tool directly with the exact SQL the user saw and possibly edited. Whatever the client sends, the server's guard and the database role are the authority.

## 2. Tech Stack

| Concern | Choice | Notes |
|---|---|---|
| Language | Java 21 | Records, sealed interfaces, pattern matching |
| Framework | Spring Boot (current stable) | Both apps |
| AI | Spring AI (**Spring AI BOM** matching the Boot version) | Never mix Spring AI versions across modules |
| MCP server | `spring-ai-starter-mcp-server-webmvc` | `@Tool` methods + `MethodToolCallbackProvider` bean |
| MCP client | `spring-ai-starter-mcp-client` | Gives `McpSyncClient` and `ToolCallbackProvider` beans |
| LLM | `spring-ai-starter-model-anthropic` (default); OpenAI / Azure OpenAI / Ollama starters via Maven profiles | Code depends only on `ChatClient` |
| Database | **PostgreSQL 16** in Docker (`postgres:16-alpine`) via `docker-compose.yml` | Port 5432 on 127.0.0.1, data in named volume `pgdata`; optional pgAdmin on :5050 |
| Migrations / seed | **Flyway** (`flyway-core` + `flyway-database-postgresql`), run programmatically at startup | Scripts in `db/migration` (§4) |
| JDBC | `org.postgresql:postgresql` + HikariCP + `JdbcTemplate` / `NamedParameterJdbcTemplate` | No JPA: we run native SQL |
| SQL parsing (guard) | **JSqlParser** | AST-based. **Never filter SQL with regex alone** |
| Web UI | Thymeleaf + vanilla JS (`fetch`) + one CSS file | HTMX is acceptable |
| Security | `spring-boot-starter-security` | Client: form login; server: API key on the MCP endpoint |
| Tests | JUnit 5, AssertJ, Spring Boot Test, MockMvc, **Testcontainers `PostgreSQLContainer`** (`postgres:16-alpine`) | Tests get their own throwaway DB and never touch the dev container |
| Build | Maven multi-module (wrapper committed) | |

> Docker must be running before the server starts (`docker compose up -d`). The server does **not** start or stop the container. To use any other PostgreSQL (a Homebrew install, a cloud instance), just change `app.db.*` (§4.6). The code is the same.

## 3. Repository Layout

```
natural-ai-query-editor/
├── CLAUDE.md
├── pom.xml                                   # parent: Boot parent + Spring AI BOM + Testcontainers BOM, modules
├── docker-compose.yml                        # postgres:16-alpine (+ optional pgAdmin, profile "tools")
├── .env.example                              # copy to .env (git-ignored)
├── mcp-sql-server/
│   ├── pom.xml
│   └── src/main/java/com/example/mcpsql/server/
│       ├── McpSqlServerApplication.java
│       ├── db/
│       │   ├── PostgresBootstrapConfig.java  # runs Flyway as admin, builds mcp_app DataSource (§4.5)
│       │   └── DbProperties.java             # @ConfigurationProperties("app.db")
│       ├── config/
│       │   ├── SqlGuardProperties.java       # @ConfigurationProperties("sqlguard")
│       │   ├── ToolConfig.java               # MethodToolCallbackProvider bean
│       │   └── SecurityConfig.java           # API-key filter on MCP endpoint
│       ├── guard/
│       │   ├── SqlGuard.java                 # parse + validate → GuardResult
│       │   ├── StatementKind.java            # SELECT, INSERT, UPDATE
│       │   ├── GuardResult.java              # sealed: Allowed(kind, sql) | Rejected(ruleId, reason)
│       │   ├── SqlScanner.java               # PostgreSQL-accurate lexing: strips comments
│       │   ├── BlockedNodeVisitor.java       # walks the AST for hidden blocked statements
│       │   └── QueryRejectedException.java
│       ├── schema/SchemaService.java         # information_schema / pg_catalog reads, cached
│       ├── exec/
│       │   ├── QueryExecutor.java            # read-only TX, LIMIT cap, timeout
│       │   └── WriteExecutor.java            # INSERT/UPDATE in TX, dry-run
│       ├── tools/
│       │   ├── SchemaTools.java              # @Tool list_tables, describe_table, schema_overview
│       │   ├── ReadTools.java                # @Tool run_select
│       │   └── WriteTools.java               # @Tool run_insert, run_update
│       ├── dto/                              # QueryResult, WriteResult, TableInfo, ColumnInfo…
│       └── audit/AuditLogger.java
│   └── src/main/resources/
│       ├── application.yml
│       └── db/migration/                     # ← Flyway scripts (single source of truth)
│           ├── V1__create_schema.sql
│           ├── V2__seed_data.sql
│           ├── V3__app_role.sql
│           └── V4__update_all_tables.sql
│   └── src/test/java/...                     # SqlGuardTest, tool ITs, permission IT
└── sql-client/
    ├── pom.xml
    └── src/main/java/com/example/mcpsql/client/
        ├── McpSqlClientApplication.java
        ├── config/{ChatClientConfig, SecurityConfig}.java
        ├── web/{PageController, QueryController, ApiExceptionHandler}.java
        ├── service/{SqlGenerationService, SqlExecutionService, McpToolInvoker, PreviewTokenService}.java
        ├── dto/{GenerateRequest, GeneratedSql, ExecuteRequest, ConfirmRequest,
        │        SelectResult, WriteResult, ApiError}.java
        └── prompt/SqlPrompts.java
    └── src/main/resources/
        ├── application.yml
        ├── prompts/sql-generator.st
        ├── templates/index.html
        └── static/{app.js, app.css}
```

## 4. Database: PostgreSQL in Docker, Schema and Sample Data

### 4.0 Local PostgreSQL (Docker)

```bash
cp .env.example .env                 # once; adjust if needed
docker compose up -d                 # starts naqe-postgres on 127.0.0.1:5432 with empty DB "shopdb"
docker compose ps                    # wait for STATUS "healthy"
```

| Task | Command |
|---|---|
| psql as admin | `docker compose exec postgres psql -U postgres -d shopdb` |
| psql as the app role (to see what the app is allowed to do) | `docker compose exec -e PGPASSWORD=mcp_app_dev postgres psql -h localhost -U mcp_app -d shopdb` |
| Logs | `docker compose logs -f postgres` |
| Stop / start (data kept) | `docker compose stop` / `docker compose start` |
| **Reset to fresh sample data** | `docker compose down -v && docker compose up -d`, then restart `mcp-sql-server` |
| pgAdmin web UI (optional) | `docker compose --profile tools up -d`, open http://localhost:5050, add server host **`postgres`**, port 5432, user `postgres` |

GUI tools on the Mac (DBeaver, IntelliJ, TablePlus, Postico) connect with host `localhost`, port `5432`, database `shopdb`, user `postgres` / password `postgres` (admin) or `mcp_app` / `mcp_app_dev` (restricted). If port 5432 is already taken (e.g. by a Homebrew Postgres), set `PG_PORT=5433` in `.env` and export the same `PG_PORT` for the server (`app.db.url` reads it).

The container only creates the empty `shopdb`. **Schema, data and roles come from Flyway in the app**, never from `docker-entrypoint-initdb.d`, so there is a single source of truth and Flyway's history table stays correct.

### 4.1 Startup sequence (what happens when `mcp-sql-server` boots)

1. `PostgresBootstrapConfig` connects to `${app.db.url}` as the **admin** user (`${app.db.admin-user}`, default `postgres`) through a short-lived, non-pooled connection.
2. It runs **Flyway** with `classpath:db/migration`. On the first start this applies:
   - `V1__create_schema.sql`: schema `shop` + 7 tables + FKs, checks and indexes
   - `V2__seed_data.sql`: sample records
   - `V3__app_role.sql`: least-privilege role `mcp_app`
   - `V4__update_all_tables.sql`: `UPDATE` on every `shop` table

   On later starts Flyway sees they are already applied and does nothing, so **your data changes persist** across restarts.
3. It re-syncs the `mcp_app` password from `${app.db.app-password}`.
4. It builds the **application `DataSource` (Hikari) as `mcp_app`**. All MCP tools use only this DataSource. The admin credentials are used only in step 1–3 and never exposed as a bean.
5. If the database is unreachable, startup fails fast with a clear message: *"PostgreSQL not reachable at … — did you run `docker compose up -d`?"*

### 4.2 Entity relationships

```
                 ┌──────────────┐ manager_id (self-FK)
                 │  employees   │◀───────┐
                 │──────────────│────────┘
                 │ employee_id  │
                 └──────┬───────┘
                        │ 1
                        │ sales_rep_id (nullable)
                        ▼ N
┌──────────────┐ 1   N ┌──────────────┐ 1   N ┌──────────────┐
│  customers   │──────▶│    orders    │──────▶│   payments   │
└──────────────┘       └──────┬───────┘       └──────────────┘
                              │ 1
                              ▼ N
                       ┌──────────────┐ N   1 ┌──────────────┐ N   1 ┌──────────────┐
                       │ order_items  │──────▶│   products   │──────▶│  categories  │
                       └──────────────┘       └──────────────┘       └──────────────┘
           (orders N ── M products, resolved by order_items)
```

### 4.3 Table definitions (schema `shop`; full DDL in `V1__create_schema.sql`)

**employees**: staff hierarchy

| Column | Type | Constraints |
|---|---|---|
| employee_id | INTEGER identity | PK |
| first_name, last_name | VARCHAR(50) | NOT NULL |
| email | VARCHAR(120) | NOT NULL, UNIQUE |
| job_title | VARCHAR(80) | NOT NULL |
| manager_id | INTEGER | FK → employees.employee_id (NULL = top) |
| hire_date | DATE | NOT NULL |
| salary | NUMERIC(10,2) | NOT NULL, > 0 |

**customers**

| Column | Type | Constraints |
|---|---|---|
| customer_id | INTEGER identity | PK |
| first_name, last_name | VARCHAR(50) | NOT NULL |
| email | VARCHAR(120) | NOT NULL, UNIQUE |
| phone | VARCHAR(20) | nullable |
| city | VARCHAR(60) | NOT NULL |
| state | VARCHAR(40) | nullable |
| country | VARCHAR(60) | NOT NULL, default 'USA' |
| loyalty_tier | VARCHAR(10) | BRONZE / SILVER / GOLD / PLATINUM |
| created_at | TIMESTAMPTZ | default now() |

**categories**

| Column | Type | Constraints |
|---|---|---|
| category_id | INTEGER identity | PK |
| name | VARCHAR(60) | NOT NULL, UNIQUE |
| description | VARCHAR(255) | |

**products**

| Column | Type | Constraints |
|---|---|---|
| product_id | INTEGER identity | PK |
| sku | VARCHAR(20) | NOT NULL, UNIQUE |
| name | VARCHAR(120) | NOT NULL |
| category_id | INTEGER | NOT NULL, FK → categories |
| unit_price | NUMERIC(10,2) | ≥ 0 |
| stock_qty | INTEGER | ≥ 0, default 0 |
| is_active | BOOLEAN | default TRUE |
| created_at | TIMESTAMPTZ | default now() |

**orders**

| Column | Type | Constraints |
|---|---|---|
| order_id | INTEGER identity | PK |
| customer_id | INTEGER | NOT NULL, FK → customers |
| sales_rep_id | INTEGER | FK → employees (nullable: web orders) |
| order_date | DATE | default CURRENT_DATE |
| status | VARCHAR(12) | NEW / PAID / SHIPPED / DELIVERED / CANCELLED |
| ship_city | VARCHAR(60) | |
| notes | VARCHAR(255) | |

**order_items**: junction table for orders ↔ products

| Column | Type | Constraints |
|---|---|---|
| order_item_id | INTEGER identity | PK |
| order_id | INTEGER | NOT NULL, FK → orders |
| product_id | INTEGER | NOT NULL, FK → products |
| quantity | INTEGER | > 0 |
| unit_price | NUMERIC(10,2) | price at time of order |
| discount_pct | NUMERIC(5,2) | 0–100, default 0 |
| | | UNIQUE (order_id, product_id) |

**payments**

| Column | Type | Constraints |
|---|---|---|
| payment_id | INTEGER identity | PK |
| order_id | INTEGER | NOT NULL, FK → orders |
| amount | NUMERIC(10,2) | > 0 |
| method | VARCHAR(12) | CARD / PAYPAL / BANK / GIFT_CARD |
| paid_at | TIMESTAMPTZ | default now() |

Business rules the LLM must know (put them in `schema_overview` and the prompt):
- **Order total** = `SUM(quantity * unit_price * (1 - discount_pct/100))` over `order_items`. There is no stored total column.
- "Revenue" excludes `CANCELLED` orders.
- Use `order_items.unit_price` for historical sales and `products.unit_price` for the current list price.

### 4.4 Sample data (`V2__seed_data.sql`)

| Table | Rows | Interesting cases for testing |
|---|---|---|
| employees | 7 | CEO → 2 managers → 4 reps (self-join / recursive CTE) |
| customers | 12 | USA, Canada, UK; customers 8 and 12 have **no orders** (LEFT JOIN / anti-join) |
| categories | 5 | Electronics, Books, Home & Kitchen, Sports, Office |
| products | 16 | 1 inactive (SP-4003), 2 out of stock, Office chair/desk products |
| orders | 20 | Jan 2025 – Jun 2026, all 5 statuses, 3 web orders with no sales rep, 1 cancelled |
| order_items | 36 | discounts on several lines, historical prices differ from list price |
| payments | 18 | order 2 paid in two parts; NEW/CANCELLED orders unpaid; **every payment total equals its order total** |

These scripts were tested on PostgreSQL 16. With the `mcp_app` role: SELECT and UPDATE work on every `shop` table; INSERT works on the 4 tables in §5.1; DELETE, TRUNCATE, DROP, ALTER and CREATE are refused by the database; INSERT into `payments`, `employees` or `categories` is refused; and `statement_timeout = 30s` is in effect.

Sample questions the finished app should answer:
- "Top 5 customers by total spend in 2025" (join + aggregate + `LIMIT`)
- "Revenue by category per month for 2025" (`date_trunc` + group by)
- "Show each employee with their manager's name" (self-join)
- "Customers who have never ordered" (anti-join)
- "Orders whose payments don't cover the total" (CTEs)
- "Increase stock of product EL-1004 by 25" (UPDATE → preview → confirm)
- "Add a new customer Jane Doe from Dallas, TX" (INSERT)
- "Delete cancelled orders" → **rejected** with reason

### 4.5 Startup code (`PostgresBootstrapConfig`), reference implementation

```java
@Configuration
@EnableConfigurationProperties(DbProperties.class)
class PostgresBootstrapConfig {

    /** Runs schema + seed + role scripts as admin. The admin DataSource is NOT a bean. */
    @Bean
    MigrateResult dbBootstrap(DbProperties p) {
        var admin = new DriverManagerDataSource(p.url(), p.adminUser(), p.adminPassword());
        try {
            var result = Flyway.configure()
                    .dataSource(admin)
                    .locations("classpath:db/migration")
                    .placeholders(Map.of("mcpAppPassword", p.appPassword(), "dbName", p.databaseName()))
                    .load()
                    .migrate();
            var jdbc = new JdbcTemplate(admin);
            String ddl = jdbc.queryForObject(
                    "SELECT format('ALTER ROLE mcp_app PASSWORD %L', ?)", String.class, p.appPassword());
            jdbc.execute(ddl);                     // keep password in sync with config
            return result;
        } catch (CannotGetJdbcConnectionException | FlywayException e) {
            throw new IllegalStateException("PostgreSQL not reachable at " + p.url()
                    + " — did you run `docker compose up -d`?", e);
        }
    }

    /** The ONLY DataSource the app uses: least-privilege mcp_app. */
    @Bean
    @DependsOn("dbBootstrap")
    DataSource dataSource(DbProperties p) {
        var cfg = new HikariConfig();
        cfg.setJdbcUrl(p.url());
        cfg.setUsername(p.appUser());
        cfg.setPassword(p.appPassword());
        cfg.setMaximumPoolSize(10);
        cfg.setPoolName("mcp-app");
        return new HikariDataSource(cfg);
    }
}
```

- Set `spring.flyway.enabled=false`, because Flyway runs manually above, as admin, before the app DataSource exists.
- `DbProperties.databaseName()` is parsed from the JDBC URL and validated against `[a-z_][a-z0-9_]*`.
- Integration tests use Testcontainers `PostgreSQLContainer("postgres:16-alpine")` with `@DynamicPropertySource` setting `app.db.url` / `admin-user` / `admin-password`. The same bootstrap code runs against it.

### 4.6 Server database configuration

```yaml
app:
  db:
    url: jdbc:postgresql://localhost:${PG_PORT:5432}/shopdb
    admin-user: ${POSTGRES_USER:postgres}           # used only for Flyway at startup
    admin-password: ${POSTGRES_PASSWORD:postgres}
    app-user: mcp_app
    app-password: ${MCP_DB_PASSWORD:mcp_app_dev}    # dev default; override outside local dev
spring:
  flyway:
    enabled: false
```

Defaults match `docker-compose.yml` / `.env.example`, so locally it works with no extra setup.

## 5. Safety Model (most important section)

Safety comes from **defence in depth**: the SQL guard, runtime limits, a read-only transaction for reads, and DB role privileges. None of these is trusted alone.

### 5.1 Allowed statements

| Kind | Allowed | Conditions |
|---|---|---|
| `SELECT` (incl. `WITH` CTE, `WITH RECURSIVE`, `JOIN`, `UNION`, subqueries, window functions) | ✅ | Any table, column or function. Runs read-only with a row cap (§5.3) |
| `INSERT INTO … VALUES` / `INSERT INTO … SELECT` (optional `RETURNING`, `ON CONFLICT DO NOTHING`) | ✅ | Target in `sqlguard.insert-allowed-tables` |
| `UPDATE … SET … [FROM …] [WHERE …]` (optional `RETURNING`) | ✅ | Any table, any number of rows, `WHERE` optional |

SELECT and UPDATE have no guard rules beyond being one real SELECT or UPDATE statement. What they can actually touch is limited only by the `mcp_app` grants (§5.4).

INSERT allowlist (matches the DB grants in `V3`): `shop.customers`, `shop.orders`, `shop.order_items`, `shop.products`.

### 5.2 Blocked (reject with rule id and a readable reason)

- `DELETE`, `TRUNCATE`, `DROP`, `ALTER`, `CREATE`, `COMMENT ON`
- `MERGE` (it can delete)
- **Data-modifying CTEs**, e.g. `WITH d AS (DELETE … RETURNING *) SELECT …`, or an `UPDATE`/`INSERT` inside a `WITH` of a `SELECT`
- `SELECT … INTO`, `CREATE TABLE AS`
- `COPY` (file and program access), `CALL`, `DO $$…$$`, `PREPARE` / `EXECUTE` / `DEALLOCATE`
- `GRANT`, `REVOKE`, role or user changes, `SET ROLE`, `SET SESSION AUTHORIZATION`
- `SET` / `RESET` / `SHOW` session settings, `BEGIN` / `COMMIT` / `ROLLBACK` / `SAVEPOINT` (the server owns transactions)
- `VACUUM`, `ANALYZE`, `CLUSTER`, `REINDEX`, `LOCK`, `LISTEN` / `NOTIFY`, `DISCARD`
- **More than one statement** per call
- Anything JSqlParser cannot parse: **fail closed**
- Literal forms that could hide a blocked statement from the parser: dollar quoting, `$n` parameters, `U&` strings, backslashes in `E''` strings, and non-ASCII characters outside literals

### 5.3 Guard and execution rules

1. Parse with `CCJSqlParserUtil.parseStatements(sql)`. Anything other than exactly one statement, or a parse error, is rejected.
2. Classify by statement type (`Select`, `Insert`, `Update`). Every other type is rejected.
3. `SqlScanner` first reads the SQL the way PostgreSQL does, removing comments and the trailing semicolon. `BlockedNodeVisitor` then walks the **whole AST** to catch statements hidden in CTEs or subqueries, and `SELECT INTO`.
4. Each tool accepts only its own kind (`run_select` → SELECT, …). There is **no** generic `execute_sql` tool.
5. **Reads** run inside `@Transactional(readOnly = true)`. The Postgres JDBC driver sets the transaction `READ ONLY`, so the database itself rejects any write that slips past the guard.
6. **Row cap**: wrap the query as `SELECT * FROM (<sql>) AS q LIMIT :max+1` (default `max-rows` 500). If `max+1` rows come back, return `max` rows and `truncated=true`.
7. **Timeout**: `JdbcTemplate.setQueryTimeout(30)`, plus the role-level `statement_timeout = 30s` set in `V3`.
8. **Writes**: run with `TransactionTemplate`. There is no limit on affected rows. `dryRun=true` executes, reports the count, then always rolls back.
9. `sqlguard.read-only=true` means the `WriteTools` bean is not registered.
10. Rejections produce the MCP tool error `REJECTED[RULE_ID]: <message>`. Stack traces never reach the client.

### 5.4 Database role (last line of defence), from `V3__app_role.sql` and `V4__update_all_tables.sql`

- The `mcp_app` role is `LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE`, with `search_path = shop`, `statement_timeout = 30s` and `idle_in_transaction_session_timeout = 60s`.
- `SELECT, UPDATE` on all `shop` tables (including tables added by later migrations), `INSERT` only on the 4 allowlisted tables, and `USAGE` on sequences.
- **No** DELETE, TRUNCATE, REFERENCES, TRIGGER or DDL. Postgres denies anything not granted, and `mcp_app` owns nothing, so DROP and ALTER fail.
- `CREATE` on `public` is revoked from `PUBLIC`.

### 5.5 Injection, identifiers, audit

- SQL from the LLM or the user is **untrusted input** and always goes through `SqlGuard`.
- Validate identifiers against `SchemaService` metadata and double-quote them. Use bind parameters for values.
- `AuditLogger` writes one JSON line per tool call: caller, tool, normalized SQL, decision and rule id, dry-run flag, duration, row count or affected rows, error code. Never log secrets or result data.

## 6. MCP Server: Tool Contract

```java
@Bean
ToolCallbackProvider sqlTools(SchemaTools schema, ReadTools read, ObjectProvider<WriteTools> write) {
    var beans = new ArrayList<Object>(List.of(schema, read));
    write.ifAvailable(beans::add);
    return MethodToolCallbackProvider.builder().toolObjects(beans.toArray()).build();
}
```

| Tool | Parameters | Returns (JSON) |
|---|---|---|
| `list_tables` | – | `[{schema, table, rowEstimate, comment}]` |
| `describe_table` | `table` (`shop.orders` or `orders`) | columns (name, type, nullable, default, PK, comment), FKs, indexes |
| `schema_overview` | – | compact text: every table/column/FK plus the business rules from §4.3 |
| `run_select` | `sql`, `maxRows?` | `{columns, rows, rowCount, truncated, elapsedMs}` |
| `run_insert` | `sql`, `dryRun` | `{affectedRows, committed, dryRun, returning?}` |
| `run_update` | `sql`, `dryRun` | `{affectedRows, committed, dryRun, returning?}` |

- Tool descriptions state the dialect (PostgreSQL), the limits, and that DELETE/TRUNCATE/DDL are rejected.
- Serialize `BigDecimal` as a string, temporal types as ISO-8601 and `bytea` as base64. Show JSON values as strings.

Server `application.yml` (besides §4.6; check property names against the Spring AI version in use):

```yaml
server.port: 8081
spring:
  ai:
    mcp:
      server:
        name: mcp-sql-server
        version: 1.0.0
        type: SYNC
sqlguard:
  read-only: false
  max-rows: 500
  query-timeout-seconds: 30
  insert-allowed-tables: [shop.customers, shop.orders, shop.order_items, shop.products]
mcp.security.api-key: ${MCP_API_KEY}
```

Verify with MCP Inspector (`npx @modelcontextprotocol/inspector`) before building the client.

## 7. Client Web Application

### 7.1 Page (`index.html` + `app.js`)

1. **Ask**: a question textarea and a **Generate SQL** button. Show a few clickable example questions from §4.4.
2. **Generated SQL**: an editable monospace textarea, a kind badge (SELECT / UPDATE / INSERT), the explanation and the tables used. Buttons: **Run** and **Regenerate**.
3. **Result**:
   - SELECT: an HTML table with a sticky header, row count, elapsed ms, a truncation notice, and NULLs shown as a muted `NULL`
   - UPDATE/INSERT: *"This will affect **N** rows"* with **Confirm** / **Cancel**, then a success panel with the committed count and any `RETURNING` rows
   - Error or rejected: a red panel with the message and rule id
4. **History** (session-scoped): question → SQL → outcome. Clicking an entry reloads it.

Use `fetch` with JSON and the CSRF token from a meta tag. Render cells with `textContent`, never `innerHTML`.

### 7.2 REST API (`QueryController`)

| Method & path | Body | Response |
|---|---|---|
| `POST /api/sql/generate` | `{question}` | `GeneratedSql {sql, kind, explanation, tablesUsed}` |
| `POST /api/sql/execute` | `{sql}` | SELECT → `SelectResult`; UPDATE/INSERT → `WriteResult` (dry run) + `previewToken` |
| `POST /api/sql/confirm` | `{sql, previewToken}` | `WriteResult` with `committed=true` |
| `GET /api/schema/tables` | – | table list for a sidebar |

- `previewToken` is an HMAC over (normalized SQL + user + issued-at) with a 5-minute TTL. `confirm` runs only the exact SQL that was previewed.
- `@RestControllerAdvice` maps `REJECTED[...]` → 422, `TIMEOUT` → 504, `DB_ERROR` → 502, all returned as `ApiError`.

### 7.3 SQL generation (`SqlGenerationService`)

- Build a `ChatClient` with the system prompt `prompts/sql-generator.st`.
- Give the ChatClient **only the schema tools** (`list_tables`, `describe_table`, `schema_overview`), filtered by name from the MCP `ToolCallbackProvider`. For a schema this small, you can instead inject `schema_overview` text straight into the prompt.
- **Never** register `run_*` tools with the ChatClient.
- Use structured output: `.call().entity(GeneratedSql.class)`.
- Prompt rules:
  - write **PostgreSQL** syntax: `LIMIT`, `ILIKE`, `date_trunc`, `EXTRACT`, `::` casts, `COALESCE`
  - output a single statement and schema-qualify tables (`shop.orders`)
  - use explicit column lists and JOIN on the known FKs
  - compute order totals using the §4.3 rule
  - UPDATE should use a precise `WHERE` unless the user asked to change every row
  - never produce DELETE, TRUNCATE, DDL, MERGE, COPY, CALL, DO or multi-statement batches. Otherwise return `kind=UNSUPPORTED` with an explanation
- Validate the response: non-empty SQL, a single statement, and a kind that matches the first keyword. Retry once, then show an error.

### 7.4 SQL execution (`SqlExecutionService`)

- Inject `List<McpSyncClient>` and pick the `sql` connection.
- Call `mcpClient.callTool(new McpSchema.CallToolRequest("run_select", Map.of("sql", sql)))`. If `isError()` is true, throw `McpToolException`. Otherwise parse the JSON text content into DTOs.
- UPDATE/INSERT: `execute` makes a `dryRun=true` call and returns a token. `confirm` verifies the token, then calls with `dryRun=false`.

Client `application.yml` (indicative):

```yaml
server.port: 8080
spring:
  ai:
    anthropic:
      api-key: ${ANTHROPIC_API_KEY}
      chat.options:
        model: ${LLM_MODEL}          # never hard-code model names in Java
        temperature: 0.0
    mcp:
      client:
        type: SYNC
        request-timeout: 60s
        sse:                          # or streamable-http, matching the server transport
          connections:
            sql:
              url: ${MCP_SERVER_URL:http://localhost:8081}
app:
  preview-token-secret: ${PREVIEW_TOKEN_SECRET}
  preview-token-ttl: 5m
```

LLM providers are switched with Maven profiles (`-Panthropic` default, `-Popenai`, `-Pazure`, `-Pollama`), with no Java changes.

## 8. Testing Requirements

- **`SqlGuardTest`** (pure JUnit, parameterized, at least 70 cases):
  - allowed:
    - joins across `orders`/`order_items`/`products`/`categories`
    - the employee self-join and `WITH RECURSIVE` hierarchy
    - window functions
    - `INSERT … RETURNING`
    - `UPDATE … FROM … WHERE`
    - unrestricted reads and updates: `UPDATE t SET a=1` (no WHERE), `SELECT pg_sleep(1)`, `SELECT * FROM pg_catalog.pg_class`, `SELECT … FOR UPDATE`
  - blocked:
    - plain destructive statements: `DELETE`, `TRUNCATE`, `DROP`, `SELECT 1; DROP TABLE x`
    - data-modifying CTEs: `WITH d AS (DELETE FROM shop.orders RETURNING *) SELECT * FROM d`
    - object creation and server-side code: `SELECT * INTO x FROM t`, `COPY t TO PROGRAM 'ls'`, `DO $$ … $$`, `CALL p()`
    - session changes: `SET ROLE postgres`
    - obfuscation: comment tricks, mixed case, odd whitespace and unicode
- **Server integration tests** (`@SpringBootTest` + Testcontainers `postgres:16-alpine`, fresh DB per test class):
  - every tool against the seed data
  - known answers: 20 orders, 12 customers, customers 8 and 12 have no orders, order 2 total = 1272.53
  - row cap, timeout (heavy `generate_series` cross join) and dry run
- **Permission IT**: as `mcp_app`, raw `DELETE`, `TRUNCATE`, `DROP` and INSERT into `payments` all fail with `permission denied` / `must be owner`. This proves the DB layer holds even with the guard bypassed.
- **Startup IT**: the app boots twice against the same container and Flyway does not re-apply or fail. With the DB down, startup fails with the "did you run docker compose up -d" message.
- **Client tests**:
  - MockMvc for the controller
  - a stub `ChatModel` for generation
  - a mocked `McpSyncClient` for execution
  - preview-token tamper and expiry tests
- **E2E** (optional): both apps, with Playwright driving the page through the §4.4 sample questions.

Commands: `./mvnw test`, `./mvnw verify -Pit`.

## 9. Coding Conventions

- Java 21. Use records for DTOs and constructor injection only.
- Configuration goes through typed `@ConfigurationProperties`. Secrets come from env vars and are never committed.
- Never build SQL by string-concatenating user or LLM input. Every query goes through the guard, with identifiers validated and quoted.
- Fail closed.
- `SqlGuard` and DTOs stay framework-free.
- Schema changes are **new** Flyway files (`V4__…`). Never edit an applied migration. Migrations live only in `mcp-sql-server/src/main/resources/db/migration`.
- Any new tool, rule or endpoint must come with tests.

## 10. Build Order (milestones)

1. Maven parent + modules. `docker compose up -d`, `PostgresBootstrapConfig` + Flyway V1–V3. The app boots and seed data is queryable as `mcp_app`
2. `SqlGuard` + exhaustive unit tests
3. Server: `SchemaService`, `SchemaTools`, `ReadTools`, verified in MCP Inspector
4. Server: `WriteTools` (TX, dry run), API-key security, audit log
5. Client: MCP connection + `SqlExecutionService` (run a hard-coded SELECT end to end)
6. Client: `SqlGenerationService` (ChatClient + schema tools + structured output)
7. Client: web page (generate, edit, run, results table) and the UPDATE/INSERT preview → confirm flow
8. Provider profiles, integration/E2E tests, README (incl. Docker quick start)

## 11. Out of Scope / Do Not Do

- Don't add a generic "execute any SQL" tool on the server.
- Don't give the LLM execution tools in the client.
- Don't expose the admin (`postgres`) DataSource as a bean or use it after bootstrap.
- Don't put schema/seed scripts in `docker-entrypoint-initdb.d`; Flyway in the app is the only path.
- Don't grant DELETE/TRUNCATE to `mcp_app` or support DELETE behind a flag unless the project owner changes this file.
- Don't store query results or credentials on disk, apart from the audit log (which holds neither).