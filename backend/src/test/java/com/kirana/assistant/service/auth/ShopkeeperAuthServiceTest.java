package com.kirana.assistant.service.auth;

import com.kirana.assistant.exception.UnauthorizedException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Pure unit tests — no Spring context, no MongoDB. */
class ShopkeeperAuthServiceTest {

    private ShopkeeperAuthService service() {
        return new ShopkeeperAuthService(new TokenStore(), "admin", "s3cret");
    }

    @Test
    void loginIssuesBearerToken() {
        ShopkeeperAuthService.IssuedToken t = service().login("admin", "s3cret");
        assertNotNull(t.getToken());
        assertEquals("admin", t.getUsername());
        assertTrue(t.getExpiresAt() > 0);
    }

    @Test
    void loginRejectsWrongPassword() {
        assertThrows(UnauthorizedException.class, () -> service().login("admin", "wrong"));
    }

    @Test
    void loginRejectsUnknownUser() {
        assertThrows(UnauthorizedException.class, () -> service().login("nobody", "s3cret"));
    }

    @Test
    void tokenValidatesAndRevokes() {
        ShopkeeperAuthService auth = service();
        String token = auth.login("admin", "s3cret").getToken();
        assertEquals("admin", auth.requireUser("Bearer " + token));
        auth.logout("Bearer " + token);
        assertThrows(UnauthorizedException.class, () -> auth.requireUser("Bearer " + token));
    }

    @Test
    void missingHeaderIsUnauthorized() {
        assertThrows(UnauthorizedException.class, () -> service().requireUser(null));
        assertThrows(UnauthorizedException.class, () -> service().requireUser("Token abc"));
    }
}
