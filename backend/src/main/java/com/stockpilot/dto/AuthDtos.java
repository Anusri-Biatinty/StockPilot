package com.stockpilot.dto;

import com.stockpilot.validation.StrongPassword;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class AuthDtos {

    private AuthDtos() {
    }

    public record RegisterRequest(
            @NotBlank @Email @Size(max = 160) String email,
            @NotBlank @StrongPassword String password,
            @NotBlank @Size(max = 120) String fullName) {
    }

    public record LoginRequest(
            @NotBlank @Email String email,
            @NotBlank @Size(max = 72) String password) {
    }

    public record AuthResponse(String accessToken, String tokenType, long expiresIn, UserDtos.Response user) {
    }

    public record UpdateProfileRequest(@NotBlank @Size(max = 120) String fullName) {
    }

    public record ChangePasswordRequest(
            @NotBlank String currentPassword,
            @NotBlank @StrongPassword String newPassword) {
    }
}
