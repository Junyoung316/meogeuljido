package com.amugeona.meogeuljido.common.exception;

import java.time.OffsetDateTime;
import java.util.List;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(CustomException.class)
    public ResponseEntity<ErrorResponse> handleCustomException(CustomException e) {
        ErrorCode errorCode = e.getErrorCode();
        return ResponseEntity.status(errorCode.getStatus())
                .body(ErrorResponse.of(errorCode.name(), e.getMessage(), null));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(MethodArgumentNotValidException e) {
        List<ErrorResponse.FieldErrorDetail> fieldErrors = e.getBindingResult().getFieldErrors().stream()
                .map(fe -> new ErrorResponse.FieldErrorDetail(fe.getField(), fe.getDefaultMessage()))
                .toList();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of(ErrorCode.VALIDATION_ERROR.name(),
                        ErrorCode.VALIDATION_ERROR.getDefaultMessage(), fieldErrors));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDeniedException(AccessDeniedException e) {
        return ResponseEntity.status(ErrorCode.FORBIDDEN.getStatus())
                .body(ErrorResponse.of(ErrorCode.FORBIDDEN));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpectedException(Exception e) {
        log.error("Unhandled exception", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.of(ErrorCode.INTERNAL_SERVER_ERROR));
    }

    public record ErrorResponse(
            String code,
            String message,
            OffsetDateTime timestamp,
            List<FieldErrorDetail> fieldErrors
    ) {
        public static ErrorResponse of(String code, String message, List<FieldErrorDetail> fieldErrors) {
            return new ErrorResponse(code, message, OffsetDateTime.now(), fieldErrors);
        }

        public static ErrorResponse of(ErrorCode errorCode) {
            return of(errorCode.name(), errorCode.getDefaultMessage(), null);
        }

        public record FieldErrorDetail(String field, String reason) {
        }
    }
}