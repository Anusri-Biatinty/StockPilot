package com.stockpilot.service;

import com.stockpilot.dto.PageResponse;
import com.stockpilot.dto.UserDtos;
import com.stockpilot.entity.Role;
import com.stockpilot.entity.RoleName;
import com.stockpilot.entity.User;
import com.stockpilot.exception.ConflictException;
import com.stockpilot.exception.NotFoundException;
import com.stockpilot.mapper.UserMapper;
import com.stockpilot.repository.RefreshTokenRepository;
import com.stockpilot.repository.RoleRepository;
import com.stockpilot.repository.UserRepository;
import com.stockpilot.security.SecurityUtils;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Administration of user accounts and role assignments.
 */
@Service
@RequiredArgsConstructor
public class UserService {

    private static final Map<RoleName, List<String>> ROLE_PERMISSIONS = Map.of(
            RoleName.ADMIN, List.of("Everything below", "Manage users and roles", "View audit logs"),
            RoleName.INVENTORY_MANAGER, List.of("View all operational data and reports",
                    "Manage products, categories and warehouses", "Receive, issue, transfer and adjust stock",
                    "Manage suppliers and purchase orders", "Approve purchase orders created by others"),
            RoleName.SALES_MANAGER, List.of("View all operational data and reports", "Manage customers",
                    "Create, confirm, ship and cancel sales orders", "Update shipment tracking"),
            RoleName.VIEWER, List.of("Read-only access to catalog, stock, orders and reports"));

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public PageResponse<UserDtos.Response> search(String search, RoleName role, Boolean enabled, Pageable pageable) {
        Specification<User> spec = (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (search != null && !search.isBlank()) {
                String like = "%" + search.trim().toLowerCase() + "%";
                p.add(cb.or(cb.like(cb.lower(root.get("email")), like), cb.like(cb.lower(root.get("fullName")), like)));
            }
            if (role != null) {
                p.add(cb.equal(root.get("role").get("name"), role));
            }
            if (enabled != null) {
                p.add(cb.equal(root.get("enabled"), enabled));
            }
            return cb.and(p.toArray(Predicate[]::new));
        };
        return PageResponse.of(userRepository.findAll(spec, pageable), UserMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public UserDtos.Response get(Long id) {
        return UserMapper.toResponse(find(id));
    }

    @Transactional
    public UserDtos.Response create(UserDtos.CreateRequest request) {
        String email = AuthService.normalizeEmail(request.email());
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new ConflictException("EMAIL_TAKEN", "An account with this email already exists");
        }
        User user = new User();
        user.setEmail(email);
        user.setFullName(request.fullName().trim());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setRole(role(request.role()));
        userRepository.save(user);
        auditService.record("USER_CREATED", "User", user.getId(), Map.of("email", email, "role", request.role()));
        return UserMapper.toResponse(user);
    }

    @Transactional
    public UserDtos.Response update(Long id, UserDtos.UpdateRequest request) {
        User user = find(id);
        Long currentUserId = SecurityUtils.requireCurrentUser().id();
        RoleName oldRole = user.roleName();
        boolean wasEnabled = user.isEnabled();

        if (user.getId().equals(currentUserId) && (request.role() != RoleName.ADMIN || !request.enabled())) {
            throw new ConflictException("SELF_LOCKOUT", "You cannot remove your own admin role or disable your own account");
        }
        boolean losingAdmin = oldRole == RoleName.ADMIN && wasEnabled
                && (request.role() != RoleName.ADMIN || !request.enabled());
        if (losingAdmin && userRepository.countByRole_NameAndEnabledTrue(RoleName.ADMIN) <= 1) {
            throw new ConflictException("LAST_ADMIN", "At least one active administrator is required");
        }

        user.setFullName(request.fullName().trim());
        user.setRole(role(request.role()));
        user.setEnabled(request.enabled());
        if (!request.enabled() || oldRole != request.role()) {
            refreshTokenRepository.revokeAllForUser(user.getId(), Instant.now());
        }

        Map<String, Object> details = new LinkedHashMap<>();
        if (oldRole != request.role()) {
            details.put("role", oldRole + " -> " + request.role());
        }
        if (wasEnabled != request.enabled()) {
            details.put("enabled", wasEnabled + " -> " + request.enabled());
        }
        auditService.record(oldRole != request.role() ? "USER_ROLE_CHANGED" : "USER_UPDATED", "User", id, details);
        return UserMapper.toResponse(user);
    }

    @Transactional(readOnly = true)
    public List<UserDtos.RoleResponse> roles() {
        return roleRepository.findAll().stream()
                .map(r -> new UserDtos.RoleResponse(r.getName(), r.getDescription(), ROLE_PERMISSIONS.get(r.getName())))
                .toList();
    }

    private User find(Long id) {
        return userRepository.findById(id).orElseThrow(() -> NotFoundException.of("User", id));
    }

    private Role role(RoleName name) {
        return roleRepository.findByName(name).orElseThrow(() -> NotFoundException.of("Role", name));
    }
}
