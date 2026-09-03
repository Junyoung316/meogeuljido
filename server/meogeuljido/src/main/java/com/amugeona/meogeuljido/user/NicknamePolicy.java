package com.amugeona.meogeuljido.user;

/**
 * user 도메인 전반에서 공유하는 닉네임 정책 상수
 * UserUpdateRequest(@Size)와 UserController.checkNickname(수동 검증)이 같은 값을
 * 각자 따로 하드코딩하다가 어긋날 위험이 있어 한 곳으로 모음
 */
public class NicknamePolicy {

    public static final int MIN_LENGTH = 2;
    public static final int MAX_LENGTH = 12;
    public static final String LENGTH_MESSAGE = "닉네임은 2~12자여야 합니다.";

    private NicknamePolicy() {
    }

}
