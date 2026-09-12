package com.kirana.assistant.controller;

import com.kirana.assistant.dto.LoginRequest;
import com.kirana.assistant.service.auth.ShopkeeperAuthService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Shopkeeper authentication. Public login; everything else needs the
 * Bearer token it returns.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final ShopkeeperAuthService auth;

    public AuthController(ShopkeeperAuthService auth) {
        this.auth = auth;
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@Valid @RequestBody LoginRequest req) {
        ShopkeeperAuthService.IssuedToken t = auth.login(req.getUsername(), req.getPassword());
        return ResponseEntity.ok(Map.of(
                "token", t.getToken(),
                "username", t.getUsername(),
                "expiresAt", t.getExpiresAt()));
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        auth.logout(authHeader);
        return ResponseEntity.ok(Map.of("status", "LOGGED_OUT"));
    }

    @GetMapping("/me")
    public ResponseEntity<Map<String, String>> me(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        return ResponseEntity.ok(Map.of("username", auth.requireUser(authHeader)));
    }
}
