package com.amugeona.meogeuljido.user.dto;

import com.amugeona.meogeuljido.user.NicknamePolicy;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UserUpdateRequest(
        @NotBlank(message = "닉네임은 필수 입니다.")
        @Size(min = NicknamePolicy.MIN_LENGTH, max = NicknamePolicy.MAX_LENGTH, message = NicknamePolicy.LENGTH_MESSAGE)
        String nickname
) {

        /**
         * compact 생성자는 필드 대입보다 먼저 실행되므로, 여기서 다듬은 값이 이후의
         * @Size 검증과 실제 저장값 모두 반영 - "앞뒤 공백만 있는 값"도 trim 후에는
         * 짧아졋서 @Size(min=2)에 걸리는 등, 검증 자체가 "진짜" 값 기준으로 이뤄짐
         */
        public UserUpdateRequest {
                nickname = nickname == null ? null : nickname.strip();
        }

}
