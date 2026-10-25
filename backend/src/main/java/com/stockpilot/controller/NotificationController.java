package com.stockpilot.controller;

import com.stockpilot.dto.NotificationDtos;
import com.stockpilot.dto.PageResponse;
import com.stockpilot.entity.NotificationSeverity;
import com.stockpilot.entity.NotificationType;
import com.stockpilot.security.AuthenticatedUser;
import com.stockpilot.security.Permissions;
import com.stockpilot.service.NotificationService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Every user only ever sees and changes their own notifications. */
@Tag(name = "Notifications")
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
@PreAuthorize(Permissions.AUTHENTICATED)
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public PageResponse<NotificationDtos.Response> list(@AuthenticationPrincipal AuthenticatedUser user,
                                                        @RequestParam(defaultValue = "false") boolean unreadOnly,
                                                        @RequestParam(required = false) NotificationType type,
                                                        @RequestParam(required = false) NotificationSeverity severity,
                                                        @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return notificationService.list(user.id(), unreadOnly, type, severity, pageable);
    }

    @GetMapping("/unread-count")
    public NotificationDtos.UnreadCount unreadCount(@AuthenticationPrincipal AuthenticatedUser user) {
        return new NotificationDtos.UnreadCount(notificationService.unreadCount(user.id()));
    }

    @PostMapping("/{id}/read")
    public NotificationDtos.Response markRead(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id) {
        return notificationService.markRead(user.id(), id, true);
    }

    @PostMapping("/{id}/unread")
    public NotificationDtos.Response markUnread(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id) {
        return notificationService.markRead(user.id(), id, false);
    }

    @PostMapping("/read-all")
    public Map<String, Integer> markAllRead(@AuthenticationPrincipal AuthenticatedUser user) {
        return Map.of("updated", notificationService.markAllRead(user.id()));
    }
}
