package com.company.mailing.exception;

import jakarta.validation.ConstraintViolationException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.validation.ObjectError;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MailServiceException.class)
    public ResponseEntity<Map<String, String>> handleMailServiceError(MailServiceException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("detail", ex.getMessage()));
    }

    @ExceptionHandler(MailSettingsRequiredException.class)
    public ResponseEntity<Map<String, String>> handleSettingsRequired(MailSettingsRequiredException ex) {
        return ResponseEntity.status(HttpStatus.PRECONDITION_REQUIRED).body(Map.of("detail", ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidation(MethodArgumentNotValidException ex) {
        String fieldErrors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(this::formatFieldError)
                .collect(Collectors.joining("; "));
        String objectErrors = ex.getBindingResult()
                .getGlobalErrors()
                .stream()
                .map(this::formatObjectError)
                .collect(Collectors.joining("; "));
        String detail = mergeDetails(fieldErrors, objectErrors);

        Map<String, String> body = new LinkedHashMap<>();
        body.put("detail", detail.isBlank() ? "Validation error." : detail);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Map<String, String>> handleConstraint(ConstraintViolationException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("detail", ex.getMessage()));
    }

    private String formatFieldError(FieldError fieldError) {
        return fieldError.getField() + ": " + fieldError.getDefaultMessage();
    }

    private String formatObjectError(ObjectError objectError) {
        return objectError.getDefaultMessage();
    }

    private String mergeDetails(String first, String second) {
        if (first == null || first.isBlank()) {
            return second == null ? "" : second;
        }
        if (second == null || second.isBlank()) {
            return first;
        }
        return first + "; " + second;
    }
}
