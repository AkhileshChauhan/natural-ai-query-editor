package com.example.mcpsql.client.web;

import com.example.mcpsql.client.dto.ApiError;
import com.example.mcpsql.client.service.McpToolException;
import com.example.mcpsql.client.service.PreviewTokenException;
import com.example.mcpsql.client.service.SqlGenerationException;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Turns failures into {@link ApiError} JSON. Only messages written to be shown reach the browser. */
@RestControllerAdvice(assignableTypes = QueryController.class)
class ApiExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(ApiExceptionHandler.class);

    private static final Pattern REJECTED = Pattern.compile("^REJECTED\\[(\\w+)]: (.*)$", Pattern.DOTALL);
    private static final Pattern CODED = Pattern.compile("^([A-Z_]+): (.*)$", Pattern.DOTALL);

    private static final String TOOL_ERROR = "TOOL_ERROR";
    private static final Map<String, HttpStatus> STATUS_BY_CODE = Map.of(
            "TIMEOUT", HttpStatus.GATEWAY_TIMEOUT,
            "DB_ERROR", HttpStatus.BAD_GATEWAY,
            "MCP_BAD_RESPONSE", HttpStatus.BAD_GATEWAY,
            "MCP_UNAVAILABLE", HttpStatus.SERVICE_UNAVAILABLE);

    @ExceptionHandler
    ResponseEntity<ApiError> toolFailed(McpToolException e) {
        String message = e.getMessage();
        Matcher rejected = REJECTED.matcher(message);
        if (rejected.matches()) {
            return error(HttpStatus.UNPROCESSABLE_CONTENT, "REJECTED", rejected.group(1), rejected.group(2));
        }
        Matcher coded = CODED.matcher(message);
        if (coded.matches() && STATUS_BY_CODE.containsKey(coded.group(1))) {
            return error(STATUS_BY_CODE.get(coded.group(1)), coded.group(1), null, coded.group(2));
        }
        return error(HttpStatus.BAD_GATEWAY, TOOL_ERROR, null, message);
    }

    @ExceptionHandler
    ResponseEntity<ApiError> previewInvalid(PreviewTokenException e) {
        return error(HttpStatus.CONFLICT, "PREVIEW_INVALID", null, e.getMessage());
    }

    @ExceptionHandler
    ResponseEntity<ApiError> generationFailed(SqlGenerationException e) {
        return error(HttpStatus.BAD_GATEWAY, "GENERATION_FAILED", null, e.getMessage());
    }

    @ExceptionHandler
    ResponseEntity<ApiError> invalidRequest(MethodArgumentNotValidException e) {
        return error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", null, "The request is missing a value or a value is too long");
    }

    @ExceptionHandler
    ResponseEntity<ApiError> unexpected(RuntimeException e) {
        LOG.error("Unexpected API failure", e);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL", null, "Something went wrong on the server");
    }

    private static ResponseEntity<ApiError> error(HttpStatus status, String code, String ruleId, String message) {
        return ResponseEntity.status(status).body(new ApiError(code, ruleId, message));
    }
}
