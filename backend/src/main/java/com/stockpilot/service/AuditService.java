package com.stockpilot.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockpilot.dto.AuditLogDtos;
import com.stockpilot.dto.PageResponse;
import com.stockpilot.entity.AuditLog;
import com.stockpilot.mapper.NotificationMapper;
import com.stockpilot.repository.AuditLogRepository;
import com.stockpilot.security.AuthenticatedUser;
import com.stockpilot.security.SecurityUtils;
import jakarta.persistence.criteria.Predicate;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Records who did what. Business audit entries join the caller's transaction, so they are only
 * persisted when the operation itself commits. Security events use {@link #recordSecurityEvent},
 * which commits independently so that, for example, failed logins are kept.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository repository;
    private final ObjectMapper objectMapper;

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(String action, String entityType, Object entityId, Map<String, ?> details) {
        AuthenticatedUser actor = SecurityUtils.currentUser().orElse(null);
        save(action, entityType, entityId, details, actor == null ? null : actor.id(),
                actor == null ? null : actor.email());
    }

    /** Same-transaction entry with an explicit actor, for events where nobody is authenticated yet (registration). */
    @Transactional(propagation = Propagation.MANDATORY)
    public void recordAs(String action, Long actorId, String actorEmail, Map<String, ?> details) {
        save(action, "User", actorId, details, actorId, actorEmail);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordSecurityEvent(String action, Long actorId, String actorEmail, Map<String, ?> details) {
        save(action, "User", actorId, details, actorId, actorEmail);
    }

    @Transactional(readOnly = true)
    public PageResponse<AuditLogDtos.Response> search(String action, String entityType, String actor,
                                                      LocalDate from, LocalDate to, Pageable pageable) {
        Specification<AuditLog> spec = (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (action != null && !action.isBlank()) {
                p.add(cb.equal(root.get("action"), action.trim().toUpperCase()));
            }
            if (entityType != null && !entityType.isBlank()) {
                p.add(cb.equal(cb.lower(root.get("entityType")), entityType.trim().toLowerCase()));
            }
            if (actor != null && !actor.isBlank()) {
                p.add(cb.like(cb.lower(root.get("actorEmail")), "%" + actor.trim().toLowerCase() + "%"));
            }
            if (from != null) {
                p.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from.atStartOfDay().toInstant(ZoneOffset.UTC)));
            }
            if (to != null) {
                p.add(cb.lessThan(root.get("createdAt"), to.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC)));
            }
            return cb.and(p.toArray(Predicate[]::new));
        };
        return PageResponse.of(repository.findAll(spec, pageable), NotificationMapper::toResponse);
    }

    private void save(String action, String entityType, Object entityId, Map<String, ?> details,
                      Long actorId, String actorEmail) {
        AuditLog entry = new AuditLog();
        entry.setAction(action);
        entry.setEntityType(entityType);
        entry.setEntityId(entityId == null ? null : String.valueOf(entityId));
        entry.setDetails(toJson(details));
        entry.setActorId(actorId);
        entry.setActorEmail(actorEmail);
        entry.setIpAddress(currentIp());
        repository.save(entry);
    }

    private String toJson(Map<String, ?> details) {
        if (details == null || details.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(details);
        } catch (JsonProcessingException e) {
            log.warn("Could not serialise audit details", e);
            return String.valueOf(details);
        }
    }

    private static String currentIp() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            HttpServletRequest request = attrs.getRequest();
            return request.getRemoteAddr();
        }
        return null;
    }
}
