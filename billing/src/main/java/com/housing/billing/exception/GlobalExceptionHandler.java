package com.housing.billing.exception;

import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.housing.billing.dto.response.ErrorResponse;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    // 404 - Resource not found
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(ResourceNotFoundException ex) {
        return ResponseEntity.status(404)
                .body(new ErrorResponse("NOT_FOUND", ex.getMessage()));
    }

    // 404 - Filter value does not exist for the tenant-scoped dataset
    @ExceptionHandler(FilterValueNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleFilterValueNotFound(FilterValueNotFoundException ex) {
        return ResponseEntity.status(404)
                .body(new ErrorResponse("FILTER_VALUE_NOT_FOUND", ex.getMessage()));
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

    // 400 - Validation failed for path/query parameters
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException ex) {
        String msg = ex.getConstraintViolations().stream()
                .map(v -> v.getMessage())
                .collect(Collectors.joining(", "));
        return ResponseEntity.status(400)
                .body(new ErrorResponse("VALIDATION_FAILED", msg));
    }

    // 400 - Method-level validation failed (Spring 6+ path/query validation)
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ErrorResponse> handleMethodValidation(HandlerMethodValidationException ex) {
        String msg = ex.getAllErrors().stream()
                .map(error -> error.getDefaultMessage())
                .collect(Collectors.joining(", "));
        return ResponseEntity.status(400)
                .body(new ErrorResponse("VALIDATION_FAILED", msg));
    }

    // 400 - Invalid JSON payload/type mismatch (e.g. number/boolean passed as quoted string)
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableJson(HttpMessageNotReadableException ex) {
        Throwable cause = ex.getCause();
        if (cause instanceof InvalidFormatException ife) {
            String fieldPath = ife.getPath().stream()
                    .map(ref -> ref.getFieldName())
                    .filter(name -> name != null && !name.isBlank())
                    .collect(Collectors.joining("."));
            String msg = fieldPath.isEmpty()
                    ? "Invalid value type in request body"
                    : fieldPath + ": invalid value type";
            return ResponseEntity.status(400)
                    .body(new ErrorResponse("VALIDATION_FAILED", msg));
        }

        Throwable illegalArg = findCause(cause, IllegalArgumentException.class);
        if (illegalArg instanceof IllegalArgumentException iae && iae.getMessage() != null && !iae.getMessage().isBlank()) {
            return ResponseEntity.status(400)
                    .body(new ErrorResponse("VALIDATION_FAILED", iae.getMessage()));
        }

        return ResponseEntity.status(400)
                .body(new ErrorResponse("VALIDATION_FAILED", "Malformed request body"));
    }

    private Throwable findCause(Throwable throwable, Class<? extends Throwable> targetType) {
        Throwable current = throwable;
        while (current != null) {
            if (targetType.isInstance(current)) {
                return current;
            }
            current = current.getCause();
        }
        return null;
    }

    // 403 - User does not have permission
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccess(AccessDeniedException ex) {
        return ResponseEntity.status(403)
                .body(new ErrorResponse("FORBIDDEN", "Unauthorized request"));
    }

    // 403 - Tenant isolation violation
    @ExceptionHandler(TenantIsolationException.class)
    public ResponseEntity<ErrorResponse> handleTenantIsolation(TenantIsolationException ex) {
        return ResponseEntity.status(403)
                .body(new ErrorResponse("FORBIDDEN", "Unauthorized request"));
    }

    // 401 - Invalid authentication credentials
    @ExceptionHandler(AuthenticationFailedException.class)
    public ResponseEntity<ErrorResponse> handleAuthenticationFailed(AuthenticationFailedException ex) {
        return ResponseEntity.status(401)
                .body(new ErrorResponse("UNAUTHORIZED", ex.getMessage()));
    }

    // 400 - Invalid request input (e.g. malformed path variables)
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleBadRequest(IllegalArgumentException ex) {
        return ResponseEntity.status(400)
                .body(new ErrorResponse("BAD_REQUEST", ex.getMessage()));
    }

    // 409 - Illegal state exception (e.g. trying to link an owner to a unit that already has an owner)
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponse> handleIllegalState(IllegalStateException ex) {
        return ResponseEntity.status(409)
                .body(new ErrorResponse("CONFLICT", ex.getMessage()));
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
