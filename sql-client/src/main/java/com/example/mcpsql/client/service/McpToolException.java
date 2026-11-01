package com.example.mcpsql.client.service;

/** An MCP tool call failed. The message is safe to show: the server's error text, or a generic one. */
public class McpToolException extends RuntimeException {

    public McpToolException(String message) {
        super(message);
    }

    public McpToolException(String message, Throwable cause) {
        super(message, cause);
    }
}
