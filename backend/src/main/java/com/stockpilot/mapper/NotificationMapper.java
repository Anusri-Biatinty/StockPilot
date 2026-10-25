package com.stockpilot.mapper;

import com.stockpilot.dto.AuditLogDtos;
import com.stockpilot.dto.NotificationDtos;
import com.stockpilot.entity.AuditLog;
import com.stockpilot.entity.Notification;

public final class NotificationMapper {

    private NotificationMapper() {
    }

    public static NotificationDtos.Response toResponse(Notification n) {
        return new NotificationDtos.Response(n.getId(), n.getType(), n.getSeverity(), n.getTitle(), n.getMessage(),
                n.getLink(), n.getReadAt() != null, n.getCreatedAt());
    }

    public static AuditLogDtos.Response toResponse(AuditLog a) {
        return new AuditLogDtos.Response(a.getId(), a.getActorId(), a.getActorEmail(), a.getAction(), a.getEntityType(),
                a.getEntityId(), a.getDetails(), a.getIpAddress(), a.getCreatedAt());
    }
}
