package com.stockpilot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Strongly typed view of the {@code app.*} configuration block.
 */
@ConfigurationProperties(prefix = "app")
public record AppProperties(Jwt jwt, Cors cors, Security security, Mail mail) {

    public record Jwt(String secret, long accessTokenMinutes, long refreshTokenDays) {
    }

    public record Cors(List<String> allowedOrigins) {
    }

    public record Security(boolean cookieSecure, int authRequestsPerMinute) {
    }

    public record Mail(boolean enabled, String from) {
    }
}
