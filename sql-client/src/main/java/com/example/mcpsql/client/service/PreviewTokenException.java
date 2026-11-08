package com.example.mcpsql.client.service;

/** The preview cannot be confirmed: it expired, was already used, or is for different SQL or another user. */
public class PreviewTokenException extends RuntimeException {

    PreviewTokenException() {
        super("This preview is no longer valid. Run the statement again to get a new preview.");
    }
}
