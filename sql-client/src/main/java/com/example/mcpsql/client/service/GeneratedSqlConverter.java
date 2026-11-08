package com.example.mcpsql.client.service;

import com.example.mcpsql.client.dto.GeneratedSql;
import org.springframework.ai.converter.BeanOutputConverter;

/**
 * The free model cannot enforce JSON, so the reply may wrap it in prose or code fences.
 * Everything outside the outermost braces is dropped before parsing.
 */
class GeneratedSqlConverter extends BeanOutputConverter<GeneratedSql> {

    GeneratedSqlConverter() {
        super(GeneratedSql.class);
    }

    @Override
    public GeneratedSql convert(String text) {
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end < start) {
            throw new IllegalArgumentException("The reply holds no JSON object");
        }
        return super.convert(text.substring(start, end + 1));
    }
}
