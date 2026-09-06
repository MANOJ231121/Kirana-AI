package com.kirana.assistant.service.auth;

import com.kirana.assistant.exception.UnauthorizedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Shopkeeper authentication (MVP).
 *
 * Credentials come from environment — never from code or the frontend:
 * {@code SHOPKEEPER_USERNAME} / {@code SHOPKEEPER_PASSWORD}.
 * On success a Bearer token is issued via {@link TokenStore}.
 *
 * Production hardening (out of MVP scope): per-user bcrypt hashes in
 * MongoDB, JWT with rotation, rate-limiting, audit log.
 */
@Service
public class ShopkeeperAuthService {

    private static final Logger log = LoggerFactory.getLogger(ShopkeeperAuthService.class);

    private final TokenStore tokens;

    @Value("${SHOPKEEPER_USERNAME:shopkeeper}")
    private String username;

    @Value("${SHOPKEEPER_PASSWORD:changeme}")
    private String password;

    @org.springframework.beans.factory.annotation.Autowired
    public ShopkeeperAuthService(TokenStore tokens) {
        this.tokens = tokens;
    }

    /** Visible for tests. */
    ShopkeeperAuthService(TokenStore tokens, String username, String password) {
        this.tokens = tokens;
        this.username = username;
        this.password = password;
    }

    public IssuedToken login(String username, String password) {
        if (username == null || password == null
                || !constantEquals(username.trim(), this.username)
                || !constantEquals(password, this.password)) {
            log.warn("Failed shopkeeper login attempt for user '{}'", username);
            throw new UnauthorizedException("Invalid username or password");
        }
        if ("changeme".equals(this.password)) {
            log.warn("SHOPKEEPER_PASSWORD is still the default — change it in .env");
        }
        String token = tokens.issue(this.username);
        return new IssuedToken(token, this.username, tokens.expiryEpochSeconds(token));
    }

    public String requireUser(String bearerHeader) {
        String user = userOf(bearerHeader);
        if (user == null) {
            throw new UnauthorizedException("Missing or invalid Authorization Bearer token");
        }
        return user;
    }

    public String userOf(String bearerHeader) {
        if (bearerHeader == null || !bearerHeader.startsWith("Bearer ")) {
            return null;
        }
        return tokens.validate(bearerHeader.substring(7).trim());
    }

    public void logout(String bearerHeader) {
        if (bearerHeader != null && bearerHeader.startsWith("Bearer ")) {
            tokens.revoke(bearerHeader.substring(7).trim());
        }
    }

    private static boolean constantEquals(String a, String b) {
        return MessageDigest.isEqual(
                a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }

    public static final class IssuedToken {
        private final String token;
        private final String username;
        private final long expiresAt;

        public IssuedToken(String token, String username, long expiresAt) {
            this.token = token;
            this.username = username;
            this.expiresAt = expiresAt;
        }

        public String getToken() {
            return token;
        }

        public String getUsername() {
            return username;
        }

        public long getExpiresAt() {
            return expiresAt;
        }
    }
}
