package com.amugeona.meogeuljido.common.exception;

import java.time.OffsetDateTime;
import java.util.List;

import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(CustomException.class)
    public ResponseEntity<ErrorResponse> handleCustomException(CustomException e) {
        ErrorCode errorCode = e.getErrorCode();
        return ResponseEntity.status(errorCode.getStatus())
                .body(ErrorResponse.of(errorCode.name(), e.getMessage(), e.getFieldErrors()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(MethodArgumentNotValidException e) {
        return badRequest(e.getBindingResult().getFieldErrors().stream()
                .map(fe -> new ErrorResponse.FieldErrorDetail(fe.getField(),
                        fe.getDefaultMessage()))
                .toList());
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResourceFoundException(NoResourceFoundException e) {
        return ResponseEntity.status(ErrorCode.NOT_FOUND.getStatus())
                .body(ErrorResponse.of(ErrorCode.NOT_FOUND));
    }

    /**
     * 요청 바디 JSON 파싱 실패(문법 오류, enum에 정의되지 않은 값 등)를 400으로 매핑
     * @Valid는 파싱이 성공해야 실행되므로, 파싱 자체가 실패하는 이 경우는 별도의 핸들러가 필요
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleHttpMessageNotReadableException(HttpMessageNotReadableException e) {
        log.warn("Malformed request body: {}", e.getMessage());
        return ResponseEntity.status(ErrorCode.VALIDATION_ERROR.getStatus())
                .body(ErrorResponse.of(ErrorCode.VALIDATION_ERROR));
    }

    /**
     * 필수 @RequestParam/@PathVariable이 요청에서 통째로 빠졌을 때 Spring이 던지는 예외
     * "값이 잘못됨"이 아니라 "값 자체가 없음"이라 @Size/@NotBlank 등 필드 검증기를 거치지 않고 여기서 별도로 잡아야 함
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingServletRequestParameterException(MissingServletRequestParameterException e) {
        log.warn("Missing required parameter: {}", e.getMessage());
        return ResponseEntity.status(ErrorCode.VALIDATION_ERROR.getStatus())
                .body(ErrorResponse.of(ErrorCode.VALIDATION_ERROR));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolationException(ConstraintViolationException e) {
        return badRequest(e.getConstraintViolations().stream()
                .map(cv -> new ErrorResponse.FieldErrorDetail(cv.getPropertyPath().toString(),
                        cv.getMessage()))
                .toList());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDeniedException(AccessDeniedException e) {
        return ResponseEntity.status(ErrorCode.FORBIDDEN.getStatus())
                .body(ErrorResponse.of(ErrorCode.FORBIDDEN));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthenticationException(AuthenticationException e) {
        return ResponseEntity.status(ErrorCode.INVALID_CREDENTIALS.getStatus())
                .body(ErrorResponse.of(ErrorCode.INVALID_CREDENTIALS));
    }

    /**
     * 애플리케이션 레벨 사전 검사와 DB 유니크 제약 사이의 경쟁  상태(동시 요청)를 잡는 안전망
     * 어느 유니크 인덱스가 결렸는지는 메시지에서 구분하지 않고, 공통적으로 "현재 상태와 충돌"(409)로 응답
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrityViolationException(DataIntegrityViolationException e) {
        log.warn("Data integrity violation: {}", e.getMessage());
        return ResponseEntity.status(ErrorCode.CONFLICT.getStatus())
                .body(ErrorResponse.of(ErrorCode.CONFLICT));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpectedException(Exception e) {
        log.error("Unhandled exception", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.of(ErrorCode.INTERNAL_SERVER_ERROR));
    }

    private ResponseEntity<ErrorResponse> badRequest(List<ErrorResponse.FieldErrorDetail> fieldErrors) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of(ErrorCode.VALIDATION_ERROR, fieldErrors));
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

        public static ErrorResponse of(ErrorCode errorCode, List<FieldErrorDetail> fieldErrors) {
            return of(errorCode.name(), errorCode.getDefaultMessage(), fieldErrors);
        }

        public record FieldErrorDetail(String field, String reason) {
        }
    }
}