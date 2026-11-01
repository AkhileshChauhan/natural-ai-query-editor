package com.example.mcpsql.server.tools;

import java.lang.reflect.Type;
import org.springframework.ai.tool.execution.ToolCallResultConverter;

/** Returns a String as it is, where the default converter would JSON-encode it with escaped newlines. */
public class PlainTextResultConverter implements ToolCallResultConverter {

    @Override
    public String convert(Object result, Type returnType) {
        return String.valueOf(result);
    }
}
