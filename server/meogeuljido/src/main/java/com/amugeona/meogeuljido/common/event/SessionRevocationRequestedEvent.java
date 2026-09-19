package com.amugeona.meogeuljido.common.event;

/**
 * "이 유저의 모든 세션(리프레시 토큰 + 이미 발급된 액세스 토큰 전부)을 강제로 무효화하라"는 요청
 * 반드시 AFTER_COMMIT 리스너에서만 처리 - 원 트랜잭션이 실제로 커밋된 뒤에만 Redis를 건드려야
 * 트랜잭션이 롤백(커밋 실패 포함)될 때 되돌릴 수 없는 부수효과가 남지 않음
 */
public record SessionRevocationRequestedEvent(Long userId) {
}
