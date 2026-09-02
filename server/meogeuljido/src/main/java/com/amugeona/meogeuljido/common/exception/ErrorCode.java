package com.amugeona.meogeuljido.common.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {

    // 공통
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "요청 값이 올바르지 않습니다."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "인증이 필요합니다."),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 일치하지 않습니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 리소스를 찾을 수 없습니다."),
    CONFLICT(HttpStatus.CONFLICT, "요청이 현재 상태와 충돌합니다."),
    TOO_MANY_REQUESTS(HttpStatus.TOO_MANY_REQUESTS, "잠시 후 다시 시도해주세요."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류가 발생했습니다."),

    // 인증/회원
    DUPLICATE_NICKNAME(HttpStatus.CONFLICT, "이미 사용 중인 닉네임입니다."),
    EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 가입된 이메일입니다."),
    CODE_MISMATCH(HttpStatus.UNAUTHORIZED, "인증코드가 일치하지 않습니다."),
    EMAIL_NOT_VERIFIED(HttpStatus.UNAUTHORIZED, "이메일 인증이 완료되지 않았습니다."),
    WITHDRAWAL_REASON_DETAIL_REQUIRED(HttpStatus.BAD_REQUEST, "기타 사유를 선택한 경우 상세 사유를 입력해야 합니다."),

    // 식당
    RESTAURANT_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않거나 접근할 수 없는 식당입니다."),
    DUPLICATE_RESTAURANT_SUSPECTED(HttpStatus.CONFLICT, "반경 내에 유사한 상호명의 식당이 이미 있습니다."),
    INVALID_STATUS_TRANSITION(HttpStatus.CONFLICT, "이미 처리된 식당입니다."),

    // 이미지
    IMAGE_ALREADY_REFERENCED(HttpStatus.CONFLICT, "이미 다른 리소스에 참조된 이미지입니다."),

    // 리뷰
    ALREADY_REPORTED(HttpStatus.CONFLICT, "이미 신고한 리뷰입니다."),

    // 즐겨찾기
    ALREADY_BOOKMARKED(HttpStatus.CONFLICT, "이미 즐겨찾기한 식당입니다."),
    BOOKMARK_NOT_FOUND(HttpStatus.NOT_FOUND, "즐겨찾기 내역이 없습니다.");

    private final HttpStatus status;
    private final String defaultMessage;

    ErrorCode(HttpStatus status, String defaultMessage) {
        this.status = status;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getDefaultMessage() {
        return defaultMessage;
    }
}