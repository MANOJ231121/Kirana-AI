package com.kirana.assistant.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Centralized error handling with a stable JSON shape:
 * { timestamp, status, message, path }
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private ResponseEntity<Map<String, Object>> body(HttpStatus status, String message, String path) {
        Map<String, Object> err = new LinkedHashMap<>();
        err.put("timestamp", LocalDateTime.now().toString());
        err.put("status", status.value());
        err.put("message", message);
        err.put("path", path);
        return ResponseEntity.status(status).body(err);
    }

    @ExceptionHandler(OrderNotFoundException.class)
    public ResponseEntity<Map<String, Object>> notFound(OrderNotFoundException ex, HttpServletRequest req) {
        return body(HttpStatus.NOT_FOUND, ex.getMessage(), req.getRequestURI());
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<Map<String, Object>> unauthorized(UnauthorizedException ex, HttpServletRequest req) {
        return body(HttpStatus.UNAUTHORIZED, ex.getMessage(), req.getRequestURI());
    }

    @ExceptionHandler({InvalidOrderException.class, InvalidStatusTransitionException.class,
            IllegalArgumentException.class})
    public ResponseEntity<Map<String, Object>> badRequest(RuntimeException ex, HttpServletRequest req) {
        log.warn("Bad request on {}: {}", req.getRequestURI(), ex.getMessage());
        return body(HttpStatus.BAD_REQUEST, ex.getMessage(), req.getRequestURI());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> validation(MethodArgumentNotValidException ex,
                                                          HttpServletRequest req) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .collect(Collectors.joining("; "));
        if (message.isBlank()) {
            message = "Invalid request";
        }
        return body(HttpStatus.BAD_REQUEST, message, req.getRequestURI());
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Map<String, Object>> constraint(ConstraintViolationException ex,
                                                          HttpServletRequest req) {
        return body(HttpStatus.BAD_REQUEST, ex.getMessage(), req.getRequestURI());
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<Map<String, Object>> mongo(DataAccessException ex, HttpServletRequest req) {
        log.error("MongoDB failure on {}", req.getRequestURI(), ex);
        return body(HttpStatus.SERVICE_UNAVAILABLE, "Database unavailable, please retry", req.getRequestURI());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> generic(Exception ex, HttpServletRequest req) {
        String path = req.getRequestURI();
        // Never leak AI/STT internals to the browser; log them instead.
        if (path.startsWith("/api/stt") || path.startsWith("/api/tts")
                || path.startsWith("/api/conversation")) {
            log.error("AI/voice failure on {}", path, ex);
            return body(HttpStatus.BAD_GATEWAY, "AI service temporarily unavailable", path);
        }
        log.error("Unhandled error on {}", path, ex);
        return body(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error", path);
    }
}
