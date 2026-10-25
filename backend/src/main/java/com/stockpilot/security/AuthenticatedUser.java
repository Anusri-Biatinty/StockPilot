package com.stockpilot.security;

import com.stockpilot.entity.RoleName;

/**
 * Principal stored in the SecurityContext for an authenticated request.
 */
public record AuthenticatedUser(Long id, String email, String fullName, RoleName role) {

    public boolean isAdmin() {
        return role == RoleName.ADMIN;
    }
}
