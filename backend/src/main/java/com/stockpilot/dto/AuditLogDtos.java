package com.stockpilot.dto;

import java.time.Instant;

public final class AuditLogDtos {

    private AuditLogDtos() {
    }

    public record Response(Long id, Long actorId, String actorEmail, String action, String entityType, String entityId,
                           String details, String ipAddress, Instant createdAt) {
    }
}
