package com.example.mcpsql.server.tools;

import com.example.mcpsql.server.dto.TableDetail;
import com.example.mcpsql.server.dto.TableInfo;
import com.example.mcpsql.server.schema.SchemaService;
import java.util.List;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Component
public class SchemaTools {

    private final SchemaService schema;

    public SchemaTools(SchemaService schema) {
        this.schema = schema;
    }

    @Tool(name = "list_tables", description = "List the tables in the PostgreSQL shop schema with row estimates and comments.")
    public List<TableInfo> listTables() {
        return schema.listTables();
    }

    @Tool(name = "describe_table", description = "Describe one table: columns, foreign keys and indexes.")
    public TableDetail describeTable(@ToolParam(description = "Table name, such as shop.orders or orders") String table) {
        return schema.describeTable(table);
    }

    @Tool(name = "schema_overview",
            description = "Every table, column and foreign key as compact text, plus the business rules for writing queries.",
            resultConverter = PlainTextResultConverter.class)
    public String schemaOverview() {
        return schema.overview();
    }
}
