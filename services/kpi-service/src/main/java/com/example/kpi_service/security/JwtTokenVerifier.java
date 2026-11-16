package com.example.kpi_service.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Date;
import java.util.Optional;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Only CHECKS tokens; auth-service creates them. The secret and the way it becomes a key
 * (resolveSecret) must be EXACTLY the same as in auth-service, else every token looks fake.
 */
@Service
public class JwtTokenVerifier {

    private final SecretKey signingKey;

    public JwtTokenVerifier(@Value("${app.security.jwt.secret}") String secret) {
        this.signingKey = Keys.hmacShaKeyFor(resolveSecret(secret));
    }

    /** The user if the token is real, not expired and complete; empty for ANY problem. */
    public Optional<AuthenticatedUser> verify(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            String username = claims.getSubject();
            Object role = claims.get("role");
            Object uid = claims.get("uid");

            if (username == null || username.isBlank() || role == null || !(uid instanceof Number number)) {
                return Optional.empty();
            }
            if (claims.getExpiration() == null || !claims.getExpiration().after(new Date())) {
                return Optional.empty();
            }
            return Optional.of(new AuthenticatedUser(number.longValue(), username, role.toString().toUpperCase()));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /** Same rule as auth-service (copied on purpose). */
    private byte[] resolveSecret(String secret) {
        String normalized = secret == null ? "" : secret.trim();
        if (normalized.matches("^[A-Za-z0-9+/=]+$") && normalized.length() >= 43) {
            try {
                return Decoders.BASE64.decode(normalized);
            } catch (IllegalArgumentException ignored) {
                // not real Base64: fall through
            }
        }
        byte[] raw = normalized.getBytes(StandardCharsets.UTF_8);
        if (raw.length >= 32) {
            return raw;
        }
        return Base64.getEncoder().encode(raw);
    }
}
