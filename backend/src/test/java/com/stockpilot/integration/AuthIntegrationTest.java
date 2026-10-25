package com.stockpilot.integration;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthIntegrationTest extends IntegrationTestBase {

    @Test
    void registerCreatesViewerAndReturnsTokens() throws Exception {
        doPost("/api/v1/auth/register", null,
                Map.of("email", "New.User@Example.com", "password", "Secret123", "fullName", "New User"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.user.email").value("new.user@example.com"))
                .andExpect(jsonPath("$.user.role").value("VIEWER"))
                .andExpect(jsonPath("$.user.passwordHash").doesNotExist())
                .andExpect(cookie().httpOnly("sp_refresh", true))
                .andExpect(cookie().path("sp_refresh", "/api/v1/auth"));
    }

    @Test
    void registerRejectsDuplicateEmailCaseInsensitively() throws Exception {
        doPost("/api/v1/auth/register", null, Map.of("email", "VIEWER@test.local", "password", "Secret123", "fullName", "X"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_TAKEN"));
    }

    @Test
    void registerValidatesPasswordStrengthAndEmail() throws Exception {
        doPost("/api/v1/auth/register", null, Map.of("email", "not-an-email", "password", "letters", "fullName", "X"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors.length()").value(2));
    }

    @Test
    void registerCannotChooseRole() throws Exception {
        doPost("/api/v1/auth/register", null,
                Map.of("email", "sneaky@example.com", "password", "Secret123", "fullName", "Sneaky", "role", "ADMIN"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.role").value("VIEWER"));
    }

    @Test
    void loginSucceedsWithCorrectPasswordAndIsAudited() throws Exception {
        doPost("/api/v1/auth/login", null, Map.of("email", "admin@test.local", "password", "Password123!"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.role").value("ADMIN"))
                .andExpect(jsonPath("$.expiresIn").isNumber());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_logs WHERE action = 'LOGIN_SUCCESS'", Integer.class))
                .isEqualTo(1);
    }

    @Test
    void loginFailsWithGenericMessageAndRecordsFailure() throws Exception {
        doPost("/api/v1/auth/login", null, Map.of("email", "admin@test.local", "password", "wrong-password1"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
        doPost("/api/v1/auth/login", null, Map.of("email", "nobody@test.local", "password", "wrong-password1"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_logs WHERE action = 'LOGIN_FAILED'", Integer.class))
                .isEqualTo(2);
    }

    @Test
    void disabledUserCannotLoginOrUseExistingToken() throws Exception {
        jdbc.update("UPDATE users SET enabled = false WHERE email = 'viewer@test.local'");
        doPost("/api/v1/auth/login", null, Map.of("email", "viewer@test.local", "password", "Password123!"))
                .andExpect(status().isForbidden());
        doGet("/api/v1/products", viewer).andExpect(status().isUnauthorized());
    }

    @Test
    void protectedEndpointsRequireValidToken() throws Exception {
        doGet("/api/v1/auth/me", null).andExpect(status().isUnauthorized());
        doGet("/api/v1/auth/me", "not.a.jwt").andExpect(status().isUnauthorized());
        doGet("/api/v1/auth/me", viewer).andExpect(status().isOk()).andExpect(jsonPath("$.email").value("viewer@test.local"));
    }

    @Test
    void refreshRotatesTokenAndDetectsReuse() throws Exception {
        MvcResult login = doPost("/api/v1/auth/login", null, Map.of("email", "sales@test.local", "password", "Password123!"))
                .andReturn();
        Cookie first = login.getResponse().getCookie("sp_refresh");
        assertThat(first).isNotNull();

        MvcResult refreshed = mvc.perform(post("/api/v1/auth/refresh").cookie(first))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andReturn();
        Cookie second = refreshed.getResponse().getCookie("sp_refresh");
        assertThat(second).isNotNull();
        assertThat(second.getValue()).isNotEqualTo(first.getValue());

        // Replaying the rotated-out token is treated as theft: all sessions are revoked...
        mvc.perform(post("/api/v1/auth/refresh").cookie(first)).andExpect(status().isUnauthorized());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM refresh_tokens WHERE revoked_at IS NULL", Integer.class))
                .isZero();
        // ...so even the newest token no longer works.
        mvc.perform(post("/api/v1/auth/refresh").cookie(second)).andExpect(status().isUnauthorized());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_logs WHERE action = 'TOKEN_REUSE_DETECTED'", Integer.class))
                .isPositive();
    }

    @Test
    void logoutRevokesRefreshToken() throws Exception {
        Cookie cookie = doPost("/api/v1/auth/login", null, Map.of("email", "sales@test.local", "password", "Password123!"))
                .andReturn().getResponse().getCookie("sp_refresh");
        mvc.perform(post("/api/v1/auth/logout").cookie(cookie))
                .andExpect(status().isNoContent())
                .andExpect(cookie().maxAge("sp_refresh", 0));
        mvc.perform(post("/api/v1/auth/refresh").cookie(cookie)).andExpect(status().isUnauthorized());
    }

    @Test
    void changePasswordRequiresCurrentPassword() throws Exception {
        doPost("/api/v1/auth/me/password", viewer, Map.of("currentPassword", "wrong", "newPassword", "Another123"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_CURRENT_PASSWORD"));
        doPost("/api/v1/auth/me/password", viewer, Map.of("currentPassword", "Password123!", "newPassword", "Another123"))
                .andExpect(status().isOk());
        doPost("/api/v1/auth/login", null, Map.of("email", "viewer@test.local", "password", "Another123"))
                .andExpect(status().isOk());
    }
}
