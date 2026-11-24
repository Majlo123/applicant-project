package com.example.people.shared;

import com.example.people.person.PersonNotFoundException;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);
    private static final Set<String> TEXT_FIELDS = Set.of("firstName", "lastName", "email");

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> validationFailed(MethodArgumentNotValidException exception) {
        // Report one message per field; "required" beats "too long", which beats format rules.
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors().stream()
                .sorted(Comparator.comparingInt(ApiExceptionHandler::priority))
                .forEach(error -> fieldErrors.putIfAbsent(error.getField(), error.getDefaultMessage()));
        return ResponseEntity.badRequest().body(new ApiError("VALIDATION_FAILED", "Please check the entered values", fieldErrors));
    }

    @ExceptionHandler(PersonNotFoundException.class)
    public ResponseEntity<ApiError> personNotFound(PersonNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiError("PERSON_NOT_FOUND", "Person not found", Map.of()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> unreadableBody(HttpMessageNotReadableException exception) {
        if (exception.getCause() instanceof UnrecognizedPropertyException) {
            return invalidRequest("Remove fields that are not part of a person", Map.of());
        }
        if (exception.getCause() instanceof MismatchedInputException mismatch && !mismatch.getPath().isEmpty()) {
            String field = mismatch.getPath().get(0).getFieldName();
            if ("dateOfBirth".equals(field)) {
                return invalidRequest("Enter the date of birth as a valid YYYY-MM-DD date", Map.of(field, "Enter a valid calendar date"));
            }
            if (TEXT_FIELDS.contains(field)) {
                return invalidRequest("Send names and email as text values", Map.of(field, "Enter a text value"));
            }
        }
        return invalidRequest("Send a valid JSON object in the request body", Map.of());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> invalidPathValue(MethodArgumentTypeMismatchException exception) {
        return invalidRequest("Use a numeric person ID", Map.of());
    }

    @ExceptionHandler({HttpMediaTypeNotSupportedException.class, MultipartException.class})
    public ResponseEntity<ApiError> unsupportedBody(Exception exception) {
        return invalidRequest("Send the request body as JSON", Map.of());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> illegalArgument(IllegalArgumentException exception, HttpServletRequest request) {
        // Spring rejects a wildcard Content-Type such as "*/*" with IllegalArgumentException; that is client input.
        if (hasWildcardContentType(request)) return invalidRequest("Send the request body as JSON", Map.of());
        return unexpected(exception);
    }

    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    public ResponseEntity<ApiError> notAcceptable(HttpMediaTypeNotAcceptableException exception) {
        // The client asked for a non-JSON response; still answer with the JSON error body.
        return ResponseEntity.status(HttpStatus.NOT_ACCEPTABLE).contentType(MediaType.APPLICATION_JSON)
                .body(new ApiError("INVALID_REQUEST", "Responses are only available as JSON", Map.of()));
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

    private static ResponseEntity<ApiError> invalidRequest(String message, Map<String, String> fieldErrors) {
        return ResponseEntity.badRequest().body(new ApiError("INVALID_REQUEST", message, fieldErrors));
    }

    private static boolean hasWildcardContentType(HttpServletRequest request) {
        try {
            return request.getContentType() != null && !MediaType.parseMediaType(request.getContentType()).isConcrete();
        } catch (InvalidMediaTypeException exception) {
            return false;
        }
    }

    private static int priority(FieldError error) {
        return switch (String.valueOf(error.getCode())) {
            case "NotBlank", "NotNull" -> 0;
            case "CodePointLength" -> 1;
            default -> 2;
        };
    }
}
