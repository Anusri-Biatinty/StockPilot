package com.stockpilot.service;

import com.stockpilot.config.AppProperties;
import com.stockpilot.dto.AuthDtos;
import com.stockpilot.dto.UserDtos;
import com.stockpilot.entity.RefreshToken;
import com.stockpilot.entity.RoleName;
import com.stockpilot.entity.User;
import com.stockpilot.exception.ApiException;
import com.stockpilot.exception.ConflictException;
import com.stockpilot.exception.NotFoundException;
import com.stockpilot.mapper.UserMapper;
import com.stockpilot.repository.RefreshTokenRepository;
import com.stockpilot.repository.RoleRepository;
import com.stockpilot.repository.UserRepository;
import com.stockpilot.security.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;

/**
 * Registration, login and token lifecycle.
 *
 * <p>Access tokens are short-lived JWTs held in browser memory. Refresh tokens are random opaque strings
 * delivered in an HttpOnly cookie and stored only as SHA-256 hashes. Every refresh rotates the token;
 * presenting an already-revoked token is treated as theft and revokes all sessions of that user.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    /** Result of a successful authentication: the JSON body plus the raw refresh token for the cookie. */
    public record AuthResult(AuthDtos.AuthResponse response, String refreshToken, Duration refreshTtl) {
    }

    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuditService auditService;
    private final AppProperties properties;

    /** Hash compared against when the email is unknown, so response time does not reveal valid accounts. */
    private volatile String dummyHash;

    /** Self-registration always creates a VIEWER; only an admin can grant other roles. */
    @Transactional
    public AuthResult register(AuthDtos.RegisterRequest request) {
        String email = normalizeEmail(request.email());
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new ConflictException("EMAIL_TAKEN", "An account with this email already exists");
        }
        User user = new User();
        user.setEmail(email);
        user.setFullName(request.fullName().trim());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setRole(roleRepository.findByName(RoleName.VIEWER).orElseThrow());
        user.setLastLoginAt(Instant.now());
        userRepository.save(user);
        auditService.recordAs("USER_REGISTERED", user.getId(), email, Map.of("role", RoleName.VIEWER));
        return issueTokens(user);
    }

    @Transactional(noRollbackFor = ApiException.class)
    public AuthResult login(AuthDtos.LoginRequest request) {
        String email = normalizeEmail(request.email());
        User user = userRepository.findByEmailIgnoreCase(email).orElse(null);
        boolean passwordOk = passwordEncoder.matches(request.password(), user == null ? dummyHash() : user.getPasswordHash());

        if (user == null || !passwordOk) {
            auditService.recordSecurityEvent("LOGIN_FAILED", user == null ? null : user.getId(), email,
                    Map.of("reason", user == null ? "unknown email" : "bad password"));
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Invalid email or password");
        }
        if (!user.isEnabled()) {
            auditService.recordSecurityEvent("LOGIN_FAILED", user.getId(), email, Map.of("reason", "account disabled"));
            throw new ApiException(HttpStatus.FORBIDDEN, "ACCOUNT_DISABLED", "This account has been disabled");
        }
        user.setLastLoginAt(Instant.now());
        auditService.recordSecurityEvent("LOGIN_SUCCESS", user.getId(), email, null);
        return issueTokens(user);
    }

    @Transactional(noRollbackFor = ApiException.class)
    public AuthResult refresh(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw invalidSession();
        }
        RefreshToken token = refreshTokenRepository.findByTokenHash(hash(rawToken)).orElseThrow(AuthService::invalidSession);
        User user = token.getUser();
        Instant now = Instant.now();

        if (token.getRevokedAt() != null) {
            int revoked = refreshTokenRepository.revokeAllForUser(user.getId(), now);
            log.warn("Refresh token reuse detected for user {}; revoked {} sessions", user.getId(), revoked);
            auditService.recordSecurityEvent("TOKEN_REUSE_DETECTED", user.getId(), user.getEmail(),
                    Map.of("revokedSessions", revoked));
            throw invalidSession();
        }
        if (!token.isActive(now) || !user.isEnabled()) {
            throw invalidSession();
        }
        token.setRevokedAt(now);
        return issueTokens(user);
    }

    @Transactional
    public void logout(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return;
        }
        refreshTokenRepository.findByTokenHash(hash(rawToken)).ifPresent(token -> {
            if (token.getRevokedAt() == null) {
                token.setRevokedAt(Instant.now());
            }
            auditService.recordSecurityEvent("LOGOUT", token.getUser().getId(), token.getUser().getEmail(), null);
        });
    }

    @Transactional(readOnly = true)
    public UserDtos.Response me(Long userId) {
        return UserMapper.toResponse(findUser(userId));
    }

    @Transactional
    public UserDtos.Response updateProfile(Long userId, AuthDtos.UpdateProfileRequest request) {
        User user = findUser(userId);
        user.setFullName(request.fullName().trim());
        return UserMapper.toResponse(user);
    }

    /** Changing the password signs out every other session and starts a fresh one for the caller. */
    @Transactional
    public AuthResult changePassword(Long userId, AuthDtos.ChangePasswordRequest request) {
        User user = findUser(userId);
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_CURRENT_PASSWORD", "Current password is incorrect");
        }
        if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PASSWORD_UNCHANGED", "New password must differ from the current one");
        }
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        refreshTokenRepository.revokeAllForUser(userId, Instant.now());
        auditService.recordSecurityEvent("PASSWORD_CHANGED", userId, user.getEmail(), null);
        return issueTokens(user);
    }

    private AuthResult issueTokens(User user) {
        String raw = newOpaqueToken();
        Duration ttl = Duration.ofDays(properties.jwt().refreshTokenDays());
        RefreshToken token = new RefreshToken();
        token.setUser(user);
        token.setTokenHash(hash(raw));
        token.setExpiresAt(Instant.now().plus(ttl));
        refreshTokenRepository.save(token);

        var body = new AuthDtos.AuthResponse(jwtService.generateAccessToken(user), "Bearer",
                jwtService.accessTokenTtlSeconds(), UserMapper.toResponse(user));
        return new AuthResult(body, raw, ttl);
    }

    private String dummyHash() {
        if (dummyHash == null) {
            dummyHash = passwordEncoder.encode(newOpaqueToken());
        }
        return dummyHash;
    }

    private User findUser(Long id) {
        return userRepository.findById(id).orElseThrow(() -> NotFoundException.of("User", id));
    }

    private static ApiException invalidSession() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "SESSION_EXPIRED", "Your session has expired. Please sign in again.");
    }

    static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static String newOpaqueToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String hash(String raw) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
