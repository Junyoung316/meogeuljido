package com.amugeona.meogeuljido.common.exception;

import lombok.Getter;

import java.util.List;

@Getter
public class CustomException extends RuntimeException {

    private final ErrorCode errorCode;
    private final List<GlobalExceptionHandler.ErrorResponse.FieldErrorDetail> fieldErrors;

    public CustomException(ErrorCode errorCode) {
        this(errorCode, errorCode.getDefaultMessage(), null);
    }

    public CustomException(ErrorCode errorCode, String message) {
        this(errorCode, message, null);
    }

    /**
     * 필드 단위 에러가 필요한 경우(예: 수동으로 검증하는 @RequestParam)에만 사용
     */
    public CustomException(ErrorCode errorCode, String message, List<GlobalExceptionHandler.ErrorResponse.FieldErrorDetail> fieldErrors) {
        super(message);
        this.errorCode = errorCode;
        this.fieldErrors = fieldErrors;

    }

}