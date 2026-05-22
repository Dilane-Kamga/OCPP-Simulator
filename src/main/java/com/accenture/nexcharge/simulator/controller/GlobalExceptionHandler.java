package com.accenture.nexcharge.simulator.controller;

import com.accenture.nexcharge.simulator.service.ChargePointNotFoundException;
import com.accenture.nexcharge.simulator.service.SessionNotFoundException;
import com.accenture.nexcharge.simulator.service.TagNotFoundException;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler({ChargePointNotFoundException.class, SessionNotFoundException.class, TagNotFoundException.class})
    public ResponseEntity<Map<String, String>> handleNotFound(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleBadRequest(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        return ResponseEntity.badRequest().body(Map.of(
                "error", "Validation failed",
                "fields", ex.getBindingResult().getFieldErrors().stream()
                        .map(e -> Map.of("field", e.getField(), "message", e.getDefaultMessage()))
                        .toList()
        ));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Map<String, String>> handleConstraintViolation(ConstraintViolationException ex) {
        String message = ex.getConstraintViolations().stream()
                .map(v -> v.getPropertyPath().toString() + ": " + v.getMessage())
                .reduce((a, b) -> a + "; " + b)
                .orElse("Constraint violation");
        return ResponseEntity.badRequest().body(Map.of("error", message));
    }
}
