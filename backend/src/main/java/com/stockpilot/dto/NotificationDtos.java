package com.stockpilot.dto;

import com.stockpilot.entity.NotificationSeverity;
import com.stockpilot.entity.NotificationType;

import java.time.Instant;

public final class NotificationDtos {

    private NotificationDtos() {
    }

    public record Response(Long id, NotificationType type, NotificationSeverity severity, String title, String message,
                           String link, boolean read, Instant createdAt) {
    }

    public record UnreadCount(long count) {
    }
}
