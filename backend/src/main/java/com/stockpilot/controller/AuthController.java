package com.stockpilot.controller;

import com.stockpilot.dto.AuthDtos;
import com.stockpilot.dto.UserDtos;
import com.stockpilot.security.AuthenticatedUser;
import com.stockpilot.security.RefreshTokenCookie;
import com.stockpilot.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Authentication")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final RefreshTokenCookie cookie;

    @Operation(summary = "Register a new account (VIEWER role)")
    @PostMapping("/register")
    public ResponseEntity<AuthDtos.AuthResponse> register(@Valid @RequestBody AuthDtos.RegisterRequest request) {
        return withCookie(HttpStatus.CREATED, authService.register(request));
    }

    @Operation(summary = "Log in and receive an access token (refresh token is set as an HttpOnly cookie)")
    @PostMapping("/login")
    public ResponseEntity<AuthDtos.AuthResponse> login(@Valid @RequestBody AuthDtos.LoginRequest request) {
        return withCookie(HttpStatus.OK, authService.login(request));
    }

    @Operation(summary = "Exchange the refresh cookie for a new access token (rotates the refresh token)")
    @PostMapping("/refresh")
    public ResponseEntity<AuthDtos.AuthResponse> refresh(
            @CookieValue(name = RefreshTokenCookie.NAME, required = false) String refreshToken) {
        return withCookie(HttpStatus.OK, authService.refresh(refreshToken));
    }

    @Operation(summary = "Revoke the current refresh token and clear the cookie")
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@CookieValue(name = RefreshTokenCookie.NAME, required = false) String refreshToken) {
        authService.logout(refreshToken);
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, cookie.clear().toString()).build();
    }

    @GetMapping("/me")
    public UserDtos.Response me(@AuthenticationPrincipal AuthenticatedUser user) {
        return authService.me(user.id());
    }

    @PutMapping("/me")
    public UserDtos.Response updateProfile(@AuthenticationPrincipal AuthenticatedUser user,
                                           @Valid @RequestBody AuthDtos.UpdateProfileRequest request) {
        return authService.updateProfile(user.id(), request);
    }

    @Operation(summary = "Change password; signs out all other sessions")
    @PostMapping("/me/password")
    public ResponseEntity<AuthDtos.AuthResponse> changePassword(@AuthenticationPrincipal AuthenticatedUser user,
                                                                @Valid @RequestBody AuthDtos.ChangePasswordRequest request) {
        return withCookie(HttpStatus.OK, authService.changePassword(user.id(), request));
    }

    private ResponseEntity<AuthDtos.AuthResponse> withCookie(HttpStatus status, AuthService.AuthResult result) {
        return ResponseEntity.status(status)
                .header(HttpHeaders.SET_COOKIE, cookie.create(result.refreshToken(), result.refreshTtl()).toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(result.response());
    }
}
