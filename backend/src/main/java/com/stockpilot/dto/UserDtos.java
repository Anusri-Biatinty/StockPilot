package com.stockpilot.dto;

import com.stockpilot.entity.RoleName;
import com.stockpilot.validation.StrongPassword;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public final class UserDtos {

    private UserDtos() {
    }

    public record Response(Long id, String email, String fullName, RoleName role, boolean enabled,
                           Instant lastLoginAt, Instant createdAt) {
    }

    public record CreateRequest(
            @NotBlank @Email @Size(max = 160) String email,
            @NotBlank @StrongPassword String password,
            @NotBlank @Size(max = 120) String fullName,
            @NotNull RoleName role) {
    }

    public record UpdateRequest(
            @NotBlank @Size(max = 120) String fullName,
            @NotNull RoleName role,
            @NotNull Boolean enabled) {
    }

    public record RoleResponse(RoleName name, String description, List<String> permissions) {
    }
}
