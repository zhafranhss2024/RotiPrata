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
        Long retryAfterSeconds = status == HttpStatus.TOO_MANY_REQUESTS ? 3600L : null;
        ApiErrorResponse body = new ApiErrorResponse(code, message, null, retryAfterSeconds);
        return ResponseEntity.status(status).body(body);
    }

    private String mapStatusToCode(HttpStatus status, String message) {
        if (status == HttpStatus.UNAUTHORIZED) {
            return "invalid_credentials";
        }
        if (status == HttpStatus.CONFLICT) {
            String normalized = message == null ? "" : message.toLowerCase();
            if (normalized.contains("username") || normalized.contains("display name") || normalized.contains("display_name")) {
                return "username_in_use";
            }
            if (normalized.contains("email")) {
                return "email_in_use";
            }
            return "conflict";
        }
        if (status == HttpStatus.TOO_MANY_REQUESTS) {
            return "rate_limited";
        }
        if (status == HttpStatus.BAD_REQUEST) {
            return "validation_error";
        }
        if (status == HttpStatus.NOT_FOUND) {
            return "not_found";
        }
        return "error";
    }
}
