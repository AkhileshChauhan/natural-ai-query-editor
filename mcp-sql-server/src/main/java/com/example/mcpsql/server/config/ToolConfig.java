package com.example.mcpsql.server.config;

import com.example.mcpsql.server.guard.SqlGuard;
import com.example.mcpsql.server.tools.ReadTools;
import com.example.mcpsql.server.tools.SchemaTools;
import com.example.mcpsql.server.tools.WriteTools;
import java.util.ArrayList;
import java.util.List;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(SqlGuardProperties.class)
class ToolConfig {

    @Bean
    SqlGuard sqlGuard(SqlGuardProperties properties) {
        return new SqlGuard(properties.insertAllowedTables());
    }

    @Bean
    ToolCallbackProvider sqlTools(SchemaTools schema, ReadTools read, ObjectProvider<WriteTools> write) {
        var beans = new ArrayList<Object>(List.of(schema, read));
        write.ifAvailable(beans::add);
        return MethodToolCallbackProvider.builder().toolObjects(beans.toArray()).build();
    }
}
