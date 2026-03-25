package com.housing.billing.exception;

import com.housing.billing.dto.response.ErrorResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    // 404 - Resource not found
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(ResourceNotFoundException ex) {
        return ResponseEntity.status(404)
                .body(new ErrorResponse("NOT_FOUND", ex.getMessage()));
    }

    // 400 - Validation failed (e.g. missing required field)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        String msg = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .collect(Collectors.joining(", "));
        return ResponseEntity.status(400)
                .body(new ErrorResponse("VALIDATION_FAILED", msg));
    }

    // 403 - User does not have permission
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccess(AccessDeniedException ex) {
        return ResponseEntity.status(403)
                .body(new ErrorResponse("FORBIDDEN", "You do not have permission"));
    }

    // 409 - Illegal state exception (e.g. trying to link an owner to a unit that already has an owner)
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<java.util.Map<String, String>> handleIllegalState(IllegalStateException ex) {
        return ResponseEntity.status(409).body(
                java.util.Map.of("code", "CONFLICT", "message", ex.getMessage())
        );
    }

    // 400 - Invalid filter syntax (e.g. malformed expression)
    @ExceptionHandler(InvalidFilterSyntaxException.class)
    public ResponseEntity<ErrorResponse> handleInvalidFilterSyntax(InvalidFilterSyntaxException ex) {
        return ResponseEntity.status(400)
                .body(new ErrorResponse("INVALID_FILTER_SYNTAX", ex.getMessage()));
    }

    // 400 - Unknown field in filter
    @ExceptionHandler(UnknownFilterFieldException.class)
    public ResponseEntity<ErrorResponse> handleUnknownFilterField(UnknownFilterFieldException ex) {
        return ResponseEntity.status(400)
                .body(new ErrorResponse("UNKNOWN_FILTER_FIELD", ex.getMessage()));
    }

    // 400 - Unsupported operator or invalid type conversion in filter
    @ExceptionHandler(UnsupportedFilterOperatorException.class)
    public ResponseEntity<ErrorResponse> handleUnsupportedFilterOperator(UnsupportedFilterOperatorException ex) {
        return ResponseEntity.status(400)
                .body(new ErrorResponse("UNSUPPORTED_FILTER_OPERATOR", ex.getMessage()));
    }

    // 500 - Unexpected error
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneral(Exception ex) {
        return ResponseEntity.status(500)
                .body(new ErrorResponse("INTERNAL_ERROR", "Something went wrong"));
    }
}
