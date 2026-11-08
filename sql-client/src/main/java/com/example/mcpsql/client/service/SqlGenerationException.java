package com.example.mcpsql.client.service;

/** The LLM gave no usable SQL. The message is safe to show. */
public class SqlGenerationException extends RuntimeException {

    SqlGenerationException(Throwable cause) {
        super("The model did not return usable SQL. It may be busy or rate limited, so try again or reword the question.", cause);
    }
}
