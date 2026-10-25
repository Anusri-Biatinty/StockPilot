package com.stockpilot.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockpilot.config.AppProperties;
import com.stockpilot.exception.ApiError;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fixed-window rate limiter for credential endpoints (login, register, refresh), keyed by client IP.
 * In-memory, so it applies per application instance; a multi-instance deployment would move this
 * to a shared store such as Redis or to the API gateway.
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Set<String> LIMITED_PATHS = Set.of(
            "/api/v1/auth/login", "/api/v1/auth/register", "/api/v1/auth/refresh");
    private static final long WINDOW_MILLIS = 60_000;

    private final int maxRequestsPerWindow;
    private final ObjectMapper objectMapper;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    public RateLimitFilter(AppProperties properties, ObjectMapper objectMapper) {
        this.maxRequestsPerWindow = properties.security().authRequestsPerMinute();
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        return !"POST".equals(request.getMethod()) || !LIMITED_PATHS.contains(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain) throws ServletException, IOException {
        long now = System.currentTimeMillis();
        String key = request.getRemoteAddr() + "|" + request.getRequestURI();
        Window window = windows.compute(key, (k, w) ->
                (w == null || now - w.start >= WINDOW_MILLIS) ? new Window(now) : w.increment());
        if (windows.size() > 10_000) {
            windows.entrySet().removeIf(e -> now - e.getValue().start >= WINDOW_MILLIS);
        }

        if (window.count > maxRequestsPerWindow) {
            long retryAfter = Math.max(1, (WINDOW_MILLIS - (now - window.start)) / 1000);
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setHeader("Retry-After", String.valueOf(retryAfter));
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            objectMapper.writeValue(response.getOutputStream(), new ApiError(Instant.now(), 429, "Too Many Requests",
                    "RATE_LIMITED", "Too many attempts. Try again in " + retryAfter + " seconds.",
                    request.getRequestURI(), List.of()));
            return;
        }
        chain.doFilter(request, response);
    }

    /** Clears all counters; used by tests. */
    public void reset() {
        windows.clear();
    }

    private static final class Window {
        private final long start;
        private int count;

        private Window(long start) {
            this.start = start;
            this.count = 1;
        }

        private Window increment() {
            count++;
            return this;
        }
    }
}
