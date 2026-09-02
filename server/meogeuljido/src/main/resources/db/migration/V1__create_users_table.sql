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
CREATE UNIQUE INDEX uq_users_email_active    ON users (email)    WHERE deleted_at IS NULL;
CREATE UNIQUE INDEX uq_users_nickname_active ON users (nickname) WHERE deleted_at IS NULL;
