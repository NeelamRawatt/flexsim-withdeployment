package com.example.auth_service.security;

import com.example.auth_service.entity.User;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests the JWT (login token) code. No Spring, no database: just Java.
 *
 * JWT = JSON Web Token. A small text with 3 parts: header.payload.signature.
 * The payload says who you are (username, role). The signature proves that
 * the server made it and nobody changed it.
 */
class JwtServiceTest {

    // Needs at least 32 characters to be a safe signing key.
    private static final String SECRET = "unit-test-secret-unit-test-secret-1234";

    private AppUserPrincipal principal(String username, String role) {
        User user = new User(username, "hash", role);
        user.setId(7L);
        return new AppUserPrincipal(user, role);
    }

    @Test
    @DisplayName("a new token has the right username and is valid for that user")
    void tokenIsValidForSameUser() {
        JwtService jwtService = new JwtService(SECRET, 10);
        AppUserPrincipal neelam = principal("neelam", "ADMIN");

        String token = jwtService.generateAccessToken(neelam);

        assertThat(token.split("\\.")).hasSize(3);                      // header.payload.signature
        assertThat(jwtService.extractUsername(token)).isEqualTo("neelam");
        assertThat(jwtService.isTokenValid(token, neelam)).isTrue();
    }

    @Test
    @DisplayName("a token is NOT valid for a different user")
    void tokenIsNotValidForOtherUser() {
        JwtService jwtService = new JwtService(SECRET, 10);
        String token = jwtService.generateAccessToken(principal("neelam", "ADMIN"));

        assertThat(jwtService.isTokenValid(token, principal("someoneElse", "ADMIN"))).isFalse();
    }

    @Test
    @DisplayName("an expired token is rejected")
    void expiredTokenIsRejected() {
        // -1 minute = the token is already expired when it is created.
        JwtService jwtService = new JwtService(SECRET, -1);
        AppUserPrincipal neelam = principal("neelam", "ADMIN");
        String token = jwtService.generateAccessToken(neelam);

        assertThatThrownBy(() -> jwtService.isTokenValid(token, neelam))
                .isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("a token that was changed by someone is rejected")
    void tamperedTokenIsRejected() {
        JwtService jwtService = new JwtService(SECRET, 10);
        String token = jwtService.generateAccessToken(principal("neelam", "ADMIN"));

        // Change the first character of the signature part.
        int lastDot = token.lastIndexOf('.');
        char first = token.charAt(lastDot + 1);
        char changed = first == 'A' ? 'B' : 'A';
        String tampered = token.substring(0, lastDot + 1) + changed + token.substring(lastDot + 2);

        assertThatThrownBy(() -> jwtService.extractUsername(tampered))
                .isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("a token signed with another secret is rejected")
    void tokenFromOtherSecretIsRejected() {
        JwtService other = new JwtService("another-secret-another-secret-another-1", 10);
        String token = other.generateAccessToken(principal("neelam", "ADMIN"));

        JwtService jwtService = new JwtService(SECRET, 10);

        assertThatThrownBy(() -> jwtService.extractUsername(token))
                .isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("max age in seconds is minutes x 60")
    void maxAgeIsInSeconds() {
        assertThat(new JwtService(SECRET, 10).getAccessTokenMaxAgeSeconds()).isEqualTo(600L);
    }
}
