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
        if (fieldErrors != null && errorCode != ErrorCode.VALIDATION_ERROR) {
            /**
             * fieldErrors는 400 VALIDATION_ERROR 전용
             */
            throw new IllegalArgumentException("fieldErrors는 VALIDATION_ERROR와만 함께 사용할 수 있습니다: " + errorCode);
        }
        this.errorCode = errorCode;
        this.fieldErrors = fieldErrors;

    }

}