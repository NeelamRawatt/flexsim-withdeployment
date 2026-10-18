package com.example.auth_service.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Date;
import java.util.Map;

/**
 * Creates and checks JWT tokens.
 *
 * JWT = "JSON Web Token". It is a small text that says "this is user X with role Y,
 * valid until time Z". The server SIGNS it with a secret key. If anybody changes even
 * one letter, the signature no longer fits and the token is rejected.
 * Example: eyJhbGciOi...(header).eyJzdWIiOiJkZXZlbG9wZXIi...(data).kQ3x...(signature)
 *
 * This is the SHORT token (10 minutes). It is signed with APP_JWT_SECRET.
 * Anybody who has the same secret can check the token. That is how the
 * API gateway will check it later, without calling this service.
 */
@Service
public class JwtService {

    private final SecretKey signingKey;
    private final long accessTokenMinutes;

    public JwtService(
            @Value("${app.security.jwt.secret}") String secret,
            @Value("${app.security.jwt.access-token-minutes}") long accessTokenMinutes
    ) {
        this.signingKey = Keys.hmacShaKeyFor(resolveSecret(secret));
        this.accessTokenMinutes = accessTokenMinutes;
    }

    public String generateAccessToken(AppUserPrincipal principal) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(accessTokenMinutes, ChronoUnit.MINUTES);

        return Jwts.builder()
                .subject(principal.getUsername())
                .claims(Map.of("role", principal.getRole(), "uid", principal.getUserId()))
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(signingKey)
                .compact();
    }

    public String extractUsername(String token) {
        return parseClaims(token).getSubject();
    }

    public boolean isTokenValid(String token, UserDetails userDetails) {
        Claims claims = parseClaims(token);
        return claims.getSubject().equalsIgnoreCase(userDetails.getUsername())
                && claims.getExpiration().after(new Date());
    }

    public long getAccessTokenMaxAgeSeconds() {
        return accessTokenMinutes * 60;
    }

    // Reads the token. This also CHECKS the signature and the expiry time;
    // a wrong or old token throws an exception here.
    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * Turns the secret text from .env into bytes for the signing key.
     * IMPORTANT: the api-gateway (Step 8) must use THE SAME rule, or it will
     * reject tokens made here.
     *  - Looks like Base64 and is long enough (43+ characters): decode it as Base64.
     *  - Otherwise, if it is 32+ characters: use the text as it is.
     *  - Otherwise (short): it is Base64-encoded to make it longer. That is weak,
     *    so always use a long random secret in production.
     */
    private byte[] resolveSecret(String secret) {
        String normalized = secret == null ? "" : secret.trim();
        if (normalized.matches("^[A-Za-z0-9+/=]+$") && normalized.length() >= 43) {
            try {
                return Decoders.BASE64.decode(normalized);
            } catch (IllegalArgumentException ignored) {
                // Not real Base64 after all: use the other rules below.
            }
        }

        byte[] raw = normalized.getBytes(StandardCharsets.UTF_8);
        if (raw.length >= 32) {
            return raw;
        }

        return Base64.getEncoder().encode(raw);
    }
}
