package com.example.people.shared;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ApiError> invalidRequest(Exception exception) {
        return ResponseEntity.badRequest().body(new ApiError("INVALID_REQUEST", "Enter valid values using YYYY-MM-DD for dates", Map.of()));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiError> unknownPath(NoResourceFoundException exception) {
        return ResponseEntity.status(404).body(new ApiError("INVALID_REQUEST", "The requested resource was not found", Map.of()));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiError> unsupportedMethod(HttpRequestMethodNotSupportedException exception) {
        return ResponseEntity.status(405).body(new ApiError("INVALID_REQUEST", "This request method is not supported", Map.of()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> unexpected(Exception exception) {
        log.error("Request could not be completed: {}", exception.getClass().getSimpleName());
        return ResponseEntity.internalServerError().body(new ApiError("INTERNAL_ERROR", "Something went wrong. Please try again.", Map.of()));
    }
}
