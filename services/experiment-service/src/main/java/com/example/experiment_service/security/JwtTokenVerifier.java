package com.example.experiment_service.security;

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
 * Only CHECKS tokens. It never creates them (auth-service does that).
 *
 * JWT = a text token with 3 parts: header, claims (the data) and a signature.
 * The signature is made with the secret APP_JWT_SECRET. Anyone who has the SAME secret can
 * check that the token is real and not changed. That is why this service does not need to
 * call auth-service for every request.
 *
 * IMPORTANT: the secret and the way it is turned into a key (resolveSecret) must be
 * EXACTLY the same as in auth-service. If they differ, every token will look fake.
 */
@Service
public class JwtTokenVerifier {

    private final SecretKey signingKey;

    public JwtTokenVerifier(@Value("${app.security.jwt.secret}") String secret) {
        // If the secret is too short, Keys.hmacShaKeyFor throws an error and the service does
        // not start. That is good: fail at start-up, not later in production.
        this.signingKey = Keys.hmacShaKeyFor(resolveSecret(secret));
    }

    /**
     * Returns the user if the token is real, not expired and complete.
     * Returns empty for ANY problem (wrong signature, expired, broken text, missing data).
     */
    public Optional<AuthenticatedUser> verify(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey)      // checks the signature
                    .build()
                    .parseSignedClaims(token)    // also throws if the token is expired
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
            // We do not say WHY (expired or fake). The caller only needs "not valid".
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
                // not real Base64: fall through to the next rule
            }
        }
        byte[] raw = normalized.getBytes(StandardCharsets.UTF_8);
        if (raw.length >= 32) {
            return raw;
        }
        return Base64.getEncoder().encode(raw);
    }
}