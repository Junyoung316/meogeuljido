CREATE TABLE users (
   id                      BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
   email                   VARCHAR(255) NOT NULL,
   password_hash           VARCHAR(255) NOT NULL,       -- BCrypt 해시
   nickname                VARCHAR(12)  NOT NULL,
   role                    VARCHAR(10)  NOT NULL DEFAULT 'USER'
       CHECK (role IN ('USER', 'ADMIN')),
   withdrawal_requested_at TIMESTAMPTZ,
   last_login_at           TIMESTAMPTZ,
   dormant_warning_sent_at TIMESTAMPTZ,
   deleted_at              TIMESTAMPTZ,
   created_at              TIMESTAMPTZ  NOT NULL DEFAULT now(),
   updated_at              TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- 탈퇴(soft delete) 후 동일 이메일/닉네임 재사용을 허용하기 위해 "삭제되지 않은 행"에만 유니크 제약을 건다.
-- 이메일은 로컬 파트 대소문자를 구분하지 않는게 사실상 표준
-- 닉네임과 마찬가지로 LOWER() 함수 인덱스로 대소문자 무시 유니크 제약을 검
CREATE UNIQUE INDEX uq_users_email_active ON users(LOWER(email)) WHERE deleted_at IS NULL;
CREATE UNIQUE INDEX uq_users_nickname_active ON users (LOWER(nickname)) WHERE deleted_at IS NULL;

CREATE INDEX idx_users_withdrawal_requested_at ON users(withdrawal_requested_at) WHERE withdrawal_requested_at IS NOT NULL;
CREATE INDEX idx_users_last_login_at ON users(last_login_at);
CREATE INDEX idx_users_dormant_warning_sent_at ON users(dormant_warning_sent_at) WHERE dormant_warning_sent_at IS NOT NULL;
