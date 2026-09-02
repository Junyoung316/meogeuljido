CREATE TABLE user_withdrawal_requests (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id         BIGINT NOT NULL REFERENCES users(id),
    reason_category VARCHAR(20) NOT NULL
      CHECK (reason_category IN ('NOT_USING', 'BUGGY', 'PRIVACY_CONCERN', 'OTHER')),
    reason_detail   VARCHAR(255),
    requested_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    cancelled_at    TIMESTAMPTZ,
    finalized_at    TIMESTAMPTZ
);

CREATE INDEX idx_user_withdrawal_requests_user_id ON user_withdrawal_requests (user_id);

-- 한 사용자당 "진행 중"(취소도 확정도 안 된) 탈퇴 요청은 동시에 최대 1건이어야 한다.
CREATE UNIQUE INDEX uq_user_withdrawal_requests_pending
    ON user_withdrawal_requests (user_id)
    WHERE cancelled_at IS NULL AND finalized_at IS NULL;
