package com.amugeona.meogeuljido.common.event;

import java.time.Instant;

public record AuditLogEvent(
        Long actorId,
        String actionType,   // CREATE | UPDATE | DELETE
        String entityType,   // RESTAURANT | REVIEW | USER | NOTICE | FAQ | INQUIRY
        Long entityId,
        String summary,
        Instant occurredAt
) {
}