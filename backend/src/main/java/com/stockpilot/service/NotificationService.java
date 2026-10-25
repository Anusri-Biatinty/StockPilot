package com.stockpilot.service;

import com.stockpilot.dto.NotificationDtos;
import com.stockpilot.dto.PageResponse;
import com.stockpilot.entity.Notification;
import com.stockpilot.entity.NotificationSeverity;
import com.stockpilot.entity.NotificationType;
import com.stockpilot.entity.RoleName;
import com.stockpilot.entity.User;
import com.stockpilot.exception.NotFoundException;
import com.stockpilot.mapper.NotificationMapper;
import com.stockpilot.repository.NotificationRepository;
import com.stockpilot.repository.UserRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Creates in-app notifications (one row per recipient) and optionally mirrors important ones by email.
 * Emails are sent only after the surrounding transaction commits, so a rolled-back operation never
 * produces an email.
 */
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository repository;
    private final UserRepository userRepository;
    private final EmailService emailService;

    @Transactional
    public void notifyRoles(Collection<RoleName> roles, NotificationType type, NotificationSeverity severity,
                            String title, String message, String link) {
        notifyUsers(userRepository.findByRole_NameInAndEnabledTrue(roles), type, severity, title, message, link);
    }

    @Transactional
    public void notifyUsers(Collection<User> users, NotificationType type, NotificationSeverity severity,
                            String title, String message, String link) {
        List<User> recipients = users.stream().filter(User::isEnabled).distinct().toList();
        for (User user : recipients) {
            Notification n = new Notification();
            n.setUser(user);
            n.setType(type);
            n.setSeverity(severity);
            n.setTitle(truncate(title, 200));
            n.setMessage(truncate(message, 1000));
            n.setLink(link);
            repository.save(n);
        }
        if (severity != NotificationSeverity.INFO) {
            List<String> emails = recipients.stream().map(User::getEmail).toList();
            afterCommit(() -> emails.forEach(email -> emailService.send(email, title, message)));
        }
    }

    @Transactional(readOnly = true)
    public PageResponse<NotificationDtos.Response> list(Long userId, boolean unreadOnly, NotificationType type,
                                                        NotificationSeverity severity, Pageable pageable) {
        Specification<Notification> spec = (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            p.add(cb.equal(root.get("user").get("id"), userId));
            if (unreadOnly) {
                p.add(cb.isNull(root.get("readAt")));
            }
            if (type != null) {
                p.add(cb.equal(root.get("type"), type));
            }
            if (severity != null) {
                p.add(cb.equal(root.get("severity"), severity));
            }
            return cb.and(p.toArray(Predicate[]::new));
        };
        return PageResponse.of(repository.findAll(spec, pageable), NotificationMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public long unreadCount(Long userId) {
        return repository.countByUserIdAndReadAtIsNull(userId);
    }

    @Transactional
    public NotificationDtos.Response markRead(Long userId, Long notificationId, boolean read) {
        Notification n = repository.findByIdAndUserId(notificationId, userId)
                .orElseThrow(() -> NotFoundException.of("Notification", notificationId));
        n.setReadAt(read ? (n.getReadAt() != null ? n.getReadAt() : Instant.now()) : null);
        return NotificationMapper.toResponse(n);
    }

    @Transactional
    public int markAllRead(Long userId) {
        return repository.markAllRead(userId, Instant.now());
    }

    private static void afterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }

    private static String truncate(String value, int max) {
        return value != null && value.length() > max ? value.substring(0, max - 1) + "…" : value;
    }
}
