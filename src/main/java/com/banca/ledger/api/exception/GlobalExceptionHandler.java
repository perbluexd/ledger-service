package com.banca.ledger.api.exception;

import com.banca.ledger.application.exception.ConflictException;
import com.banca.ledger.application.exception.NotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.Instant;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    // ✅ Body validation (@Valid @RequestBody)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(
            MethodArgumentNotValidException ex,
            HttpServletRequest request
    ) {
        String message = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(this::formatFieldError)
                .collect(Collectors.joining("; "));

        return build(HttpStatus.BAD_REQUEST, message, request);
    }

    // ✅ Param validation (@Validated + constraints en @PathVariable/@RequestParam)
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleConstraintViolation(
            ConstraintViolationException ex,
            HttpServletRequest request
    ) {
        String message = ex.getConstraintViolations()
                .stream()
                .map(this::formatConstraintViolation)
                .collect(Collectors.joining("; "));

        return build(HttpStatus.BAD_REQUEST, message, request);
    }

    // ✅ Type mismatch: Long/UUID/Instant inválidos
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiErrorResponse> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex,
            HttpServletRequest request
    ) {
        // Ej: /entries/abc cuando espera Long, o UUID inválido, o Instant inválido
        String message = "Parámetro inválido: " + ex.getName();
        return build(HttpStatus.BAD_REQUEST, message, request);
    }

    // ✅ Missing required query param
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiErrorResponse> handleMissingRequestParam(
            MissingServletRequestParameterException ex,
            HttpServletRequest request
    ) {
        String message = "Falta el parámetro requerido: " + ex.getParameterName();
        return build(HttpStatus.BAD_REQUEST, message, request);
    }

    // ✅ Service/domain validation (si sigues lanzando IllegalArgumentException)
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalArgument(
            IllegalArgumentException ex,
            HttpServletRequest request
    ) {
        // Opcional: si ex.getMessage() viene null, evita "null"
        String message = ex.getMessage() != null ? ex.getMessage() : "Argumento inválido";
        return build(HttpStatus.BAD_REQUEST, message, request);
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound(
            NotFoundException ex,
            HttpServletRequest request
    ) {
        String message = ex.getMessage() != null ? ex.getMessage() : "Not found";
        return build(HttpStatus.NOT_FOUND, message, request);
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ApiErrorResponse> handleConflict(
            ConflictException ex,
            HttpServletRequest request
    ) {
        String message = ex.getMessage() != null ? ex.getMessage() : "Conflict";
        return build(HttpStatus.CONFLICT, message, request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(
            Exception ex,
            HttpServletRequest request
    ) {
        // Aquí NO expongas detalles internos al cliente
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error", request);
    }

    private ResponseEntity<ApiErrorResponse> build(
            HttpStatus status,
            String message,
            HttpServletRequest request
    ) {
        ApiErrorResponse body = ApiErrorResponse.builder()
                .timestamp(Instant.now())
                .status(status.value())
                .error(status.getReasonPhrase())
                .message(message)
                .path(request.getRequestURI())
                .traceId(null)
                .build();

        return ResponseEntity.status(status).body(body);
    }

    private String formatFieldError(FieldError fe) {
        // Ej: "amount: must be greater than 0"
        return fe.getField() + ": " + fe.getDefaultMessage();
    }

    private String formatConstraintViolation(ConstraintViolation<?> v) {
        // PropertyPath suele ser largo: "getEntryDetail.entryId"
        // Si quieres, puedes recortarlo (opcional). Por ahora lo dejamos tal cual.
        return v.getPropertyPath() + ": " + v.getMessage();
    }
}
