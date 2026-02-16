package com.rotiprata.api;

import com.rotiprata.api.dto.ApiErrorResponse;
import java.util.HashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = new HashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            if (!fieldErrors.containsKey(fieldError.getField())) {
                fieldErrors.put(fieldError.getField(), fieldError.getDefaultMessage());
            }
        }
        ApiErrorResponse body = new ApiErrorResponse(
            "validation_error",
            "Validation failed",
            fieldErrors,
            null
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiErrorResponse> handleResponseStatus(ResponseStatusException ex) {
        HttpStatus status = HttpStatus.valueOf(ex.getStatusCode().value());
        String message = ex.getReason() != null && !ex.getReason().isBlank()
            ? ex.getReason()
            : status.getReasonPhrase();
        String code = mapStatusToCode(status, message);
        ApiErrorResponse body = new ApiErrorResponse(code, message, null, null);
        return ResponseEntity.status(status).body(body);
    }

    private String mapStatusToCode(HttpStatus status, String message) {
        return switch (status) {
            case UNAUTHORIZED -> "invalid_credentials";
            case CONFLICT -> {
                String normalized = message == null ? "" : message.toLowerCase();
                if (normalized.contains("username") || normalized.contains("display name") || normalized.contains("display_name")) {
                    yield "username_in_use";
                }
                yield "email_in_use";
            }
            case TOO_MANY_REQUESTS -> "rate_limited";
            case BAD_REQUEST -> "validation_error";
            case NOT_FOUND -> "not_found";
            default -> "error";
        };
    }
}
