package com.example.mcpsql.server.tools;

import com.example.mcpsql.server.audit.AuditLogger;
import com.example.mcpsql.server.dto.TableDetail;
import com.example.mcpsql.server.dto.TableInfo;
import com.example.mcpsql.server.schema.SchemaService;
import java.util.List;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Component
public class SchemaTools {

    private static final int NO_ROWS = 0;

    private final SchemaService schema;
    private final AuditLogger audit;

    SchemaTools(SchemaService schema, AuditLogger audit) {
        this.schema = schema;
        this.audit = audit;
    }

    @Tool(name = "list_tables", description = "List the tables in the PostgreSQL shop schema with row estimates and comments.")
    public List<TableInfo> listTables(ToolContext context) {
        return audit.audited(context, "list_tables", null, null, schema::listTables, List::size);
    }

    @Tool(name = "describe_table", description = "Describe one table: columns, foreign keys, check constraints and indexes.")
    public TableDetail describeTable(
            @ToolParam(description = "Table name, such as shop.orders or orders") String table,
            ToolContext context) {
        return audit.audited(context, "describe_table", null, null,
                () -> schema.describeTable(table), detail -> detail.columns().size());
    }

    @Tool(name = "schema_overview",
            description = "Every table, column, foreign key and check constraint as compact text, plus the business rules for writing queries.",
            resultConverter = PlainTextResultConverter.class)
    public String schemaOverview(ToolContext context) {
        return audit.audited(context, "schema_overview", null, null, schema::overview, text -> NO_ROWS);
    }
}
