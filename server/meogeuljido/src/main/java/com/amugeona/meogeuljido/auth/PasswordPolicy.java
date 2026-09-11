package com.amugeona.meogeuljido.auth;

/**
 * auth 도메인에서 비밀번호 관련 검증에 공유하는 정책 상수
 * SignupRequest와 PasswordResetConfirmRequest가 같은 값을 각자 하드코딩하다가
 * 어긋날 위험이 있어 한 곳으로 모음(NicknamePolicy와 동일한 이유)
 */
public class PasswordPolicy {

    public static final int MIN_PASSWORD_LENGTH = 8;
    public static final int MAX_PASSWORD_LENGTH = 64;
    public static final String LENGTH_MESSAGE = "비밀번호는 8~64자여야 합니다.";

    private PasswordPolicy() {
    }
}
