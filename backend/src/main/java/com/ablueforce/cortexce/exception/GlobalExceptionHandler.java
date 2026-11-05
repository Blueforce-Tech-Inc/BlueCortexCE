package com.ablueforce.cortexce.exception;

import com.ablueforce.cortexce.common.ErrorResponses;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Turns framework-level failures into the same error envelope handlers already return.
 *
 * <p>Spring used to answer type-conversion failures, missing parameters and unknown routes
 * with its default body ({@code timestamp/status/error/path}), so a client saw a different
 * JSON shape depending on which layer failed. This advice renders those failures as
 * {@link ErrorResponses} with the HTTP status preserved.
 *
 * <p>Messages stay generic where the underlying exception could leak internals: an uncaught
 * exception answers {@code 500 internal_error} with a fixed message. Handlers that already
 * catch their failures and return a specific message keep doing so.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Framework failures that already carry an HTTP status: {@link ResponseStatusException},
     * unsupported media type, and not-acceptable. The status is preserved; only the body is
     * rewritten into the envelope.
     */
    @ExceptionHandler({ResponseStatusException.class,
        org.springframework.web.HttpMediaTypeNotSupportedException.class,
        org.springframework.web.HttpMediaTypeNotAcceptableException.class})
    public ResponseEntity<Object> handleFramework(Exception exception) {
        int status = 500;
        if (exception instanceof ResponseStatusException statusException) {
            status = statusException.getStatusCode().value();
        } else if (exception instanceof org.springframework.web.ErrorResponse errorResponse) {
            status = errorResponse.getStatusCode().value();
        }
        String message = statusPhrase(status);
        if (exception instanceof ResponseStatusException statusException
            && statusException.getReason() != null && !statusException.getReason().isBlank()) {
            message = statusException.getReason();
        }
        return ResponseEntity.status(status)
            .body(ErrorResponses.body(ErrorResponses.codeForStatus(status), message));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Object> handleValidation(MethodArgumentNotValidException exception) {
        List<Map<String, String>> issues = new ArrayList<>();
        for (FieldError error : exception.getBindingResult().getFieldErrors()) {
            issues.add(Map.of("field", error.getField(),
                "message", error.getDefaultMessage() == null ? "invalid" : error.getDefaultMessage()));
        }
        return ResponseEntity.badRequest().body(ErrorResponses.body(
            ErrorResponses.INVALID_REQUEST, "Request validation failed", Map.of("issues", issues)));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Object> handleMissingParameter(MissingServletRequestParameterException exception) {
        return ResponseEntity.badRequest().body(ErrorResponses.body(
            ErrorResponses.INVALID_REQUEST,
            "Missing required parameter: " + exception.getParameterName()));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Object> handleTypeMismatch(MethodArgumentTypeMismatchException exception) {
        return ResponseEntity.badRequest().body(ErrorResponses.body(
            ErrorResponses.INVALID_REQUEST,
            "Invalid value for parameter: " + exception.getName()));
    }

    @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
    public ResponseEntity<Object> handleUnreadableBody(Exception exception) {
        // A chunked body over cortexce.http.max-body-bytes is cut off while it is parsed;
        // report the real reason (413) instead of the generic malformed-body message.
        if (hasCause(exception, com.ablueforce.cortexce.common.PayloadTooLargeException.class)) {
            return payloadTooLarge(exception);
        }
        return ResponseEntity.badRequest().body(ErrorResponses.body(
            ErrorResponses.INVALID_REQUEST, "Malformed or unreadable request body"));
    }

    @ExceptionHandler(com.ablueforce.cortexce.common.PayloadTooLargeException.class)
    public ResponseEntity<Object> handlePayloadTooLarge(Exception exception) {
        return payloadTooLarge(exception);
    }

    private static ResponseEntity<Object> payloadTooLarge(Exception exception) {
        com.ablueforce.cortexce.common.PayloadTooLargeException cause = findCause(
            exception, com.ablueforce.cortexce.common.PayloadTooLargeException.class);
        if (cause != null) {
            return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(ErrorResponses.body(ErrorResponses.PAYLOAD_TOO_LARGE, cause.getMessage(),
                    Map.of("max_bytes", cause.getLimitBytes())));
        }
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
            .body(ErrorResponses.body(ErrorResponses.PAYLOAD_TOO_LARGE, "Request body too large"));
    }

    private static <T extends Throwable> T findCause(Throwable throwable, Class<T> type) {
        Throwable current = throwable;
        // Bounded walk: an exception chain is short, but a self-referential cause would loop.
        for (int depth = 0; current != null && depth < 16; depth++) {
            if (type.isInstance(current)) {
                return type.cast(current);
            }
            current = current.getCause();
        }
        return null;
    }

    private static boolean hasCause(Throwable throwable, Class<? extends Throwable> type) {
        return findCause(throwable, type) != null;
    }

    @ExceptionHandler(org.springframework.web.HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Object> handleMethodNotSupported(Exception exception) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
            .body(ErrorResponses.body(ErrorResponses.INVALID_REQUEST, "Method not allowed"));
    }

    @ExceptionHandler(org.springframework.web.servlet.resource.NoResourceFoundException.class)
    public ResponseEntity<Object> handleNoResource(Exception exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(ErrorResponses.body(ErrorResponses.NOT_FOUND, "Resource not found"));
    }

    private static String statusPhrase(int status) {
        HttpStatus resolved = HttpStatus.resolve(status);
        return resolved == null ? "Error" : resolved.getReasonPhrase();
    }

    /**
     * Database failures.
     *
     * <p>A read-only database (SQLSTATE 25006) is an operational state, not a bug: the
     * write cannot succeed until the volume or the replica becomes writable, and retrying
     * later is the right reaction. Answering 503 with a sentence that names the cause is
     * what a client can act on; the previous generic "Internal server error" was not.
     *
     * <p>Every other data-access failure keeps the old 500/{@code internal_error} answer,
     * so this handler only changes the read-only case.
     */
    @ExceptionHandler(org.springframework.dao.DataAccessException.class)
    public ResponseEntity<Object> handleDataAccess(org.springframework.dao.DataAccessException exception) {
        if (isReadOnlyTransaction(exception)) {
            log.warn("Database rejected a write because the session is read-only: {}",
                rootMessage(exception));
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ErrorResponses.body(ErrorResponses.SERVICE_UNAVAILABLE,
                    "Database is read-only: the write was rejected. Retry after the database "
                        + "accepts writes."));
        }
        log.error("Data access failed", exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ErrorResponses.body(ErrorResponses.INTERNAL_ERROR, "Internal server error"));
    }

    /** SQLSTATE 25006 is PostgreSQL's "read_only_sql_transaction". */
    private static boolean isReadOnlyTransaction(Throwable exception) {
        Throwable cause = exception;
        while (cause != null) {
            if (cause instanceof java.sql.SQLException sql
                && "25006".equals(sql.getSQLState())) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }

    private static String rootMessage(Throwable exception) {
        Throwable cause = exception;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleUnexpected(Exception exception) {
        log.error("Unhandled exception", exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ErrorResponses.body(ErrorResponses.INTERNAL_ERROR, "Internal server error"));
    }
}
