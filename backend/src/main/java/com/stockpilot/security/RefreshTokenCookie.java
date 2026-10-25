package com.stockpilot.security;

import com.stockpilot.config.AppProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Builds the refresh-token cookie: HttpOnly (not readable by JavaScript), SameSite=Strict (not sent on
 * cross-site requests), Secure outside local development, and scoped to the auth endpoints only.
 */
@Component
@RequiredArgsConstructor
public class RefreshTokenCookie {

    public static final String NAME = "sp_refresh";
    private static final String PATH = "/api/v1/auth";

    private final AppProperties properties;

    public ResponseCookie create(String token, Duration ttl) {
        return base(token).maxAge(ttl).build();
    }

    public ResponseCookie clear() {
        return base("").maxAge(0).build();
    }

    private ResponseCookie.ResponseCookieBuilder base(String value) {
        return ResponseCookie.from(NAME, value)
                .httpOnly(true)
                .secure(properties.security().cookieSecure())
                .sameSite("Strict")
                .path(PATH);
    }
}
