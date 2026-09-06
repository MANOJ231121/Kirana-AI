package com.kirana.assistant.service.auth;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory Bearer token store for the shopkeeper portal (MVP auth).
 * Tokens expire after 12h. Single-instance friendly; replace with
 * Redis/JWT for multi-instance production.
 */
@Service
public class TokenStore {

    private static final long TTL_SECONDS = 12 * 60 * 60;

    private final Map<String, Token> tokens = new ConcurrentHashMap<>();

    public String issue(String username) {
        String token = UUID.randomUUID() + "." + UUID.randomUUID().toString().replace("-", "");
        tokens.put(token, new Token(username, Instant.now().plusSeconds(TTL_SECONDS)));
        return token;
    }

    public String validate(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        Token t = tokens.get(token);
        if (t == null) {
            return null;
        }
        if (Instant.now().isAfter(t.expiresAt)) {
            tokens.remove(token);
            return null;
        }
        return t.username;
    }

    public void revoke(String token) {
        if (token != null) {
            tokens.remove(token);
        }
    }

    public long expiryEpochSeconds(String token) {
        Token t = tokens.get(token);
        return t == null ? 0 : t.expiresAt.getEpochSecond();
    }

    private static final class Token {
        final String username;
        final Instant expiresAt;

        Token(String username, Instant expiresAt) {
            this.username = username;
            this.expiresAt = expiresAt;
        }
    }
}
