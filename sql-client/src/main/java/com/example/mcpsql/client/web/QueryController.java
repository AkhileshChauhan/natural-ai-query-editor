package com.example.mcpsql.client.web;

import com.example.mcpsql.client.dto.ConfirmRequest;
import com.example.mcpsql.client.dto.ExecuteRequest;
import com.example.mcpsql.client.dto.ExecuteResponse;
import com.example.mcpsql.client.dto.GenerateRequest;
import com.example.mcpsql.client.dto.GeneratedSql;
import com.example.mcpsql.client.dto.TableInfo;
import com.example.mcpsql.client.dto.WriteResult;
import com.example.mcpsql.client.service.SqlExecutionService;
import com.example.mcpsql.client.service.SqlGenerationService;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
class QueryController {

    private final SqlGenerationService generation;
    private final SqlExecutionService execution;

    QueryController(SqlGenerationService generation, SqlExecutionService execution) {
        this.generation = generation;
        this.execution = execution;
    }

    @PostMapping("/sql/generate")
    GeneratedSql generate(@Valid @RequestBody GenerateRequest request) {
        return generation.generate(request.question());
    }

    @PostMapping("/sql/execute")
    ExecuteResponse execute(@Valid @RequestBody ExecuteRequest request, Principal user) {
        return execution.execute(request.sql(), user.getName());
    }

    @PostMapping("/sql/confirm")
    WriteResult confirm(@Valid @RequestBody ConfirmRequest request, Principal user) {
        return execution.confirm(request.sql(), request.previewToken(), user.getName());
    }

    @GetMapping("/schema/tables")
    List<TableInfo> tables() {
        return execution.tables();
    }
}
