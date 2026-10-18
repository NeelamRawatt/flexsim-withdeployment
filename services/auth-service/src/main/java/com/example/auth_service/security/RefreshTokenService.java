package com.example.auth_service.security;

import com.example.auth_service.entity.RefreshToken;
import com.example.auth_service.entity.User;
import com.example.auth_service.repository.RefreshTokenRepository;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.UUID;

/**
 * Handles the REFRESH token: a longer-life ticket (1 hour here) kept in the database.
 *
 * ROTATION = every time a refresh token is used, we make a NEW one and cancel the
 * old one. So if a hacker steals an old token, it is already dead.
 */
@Service
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;

    @Value("${app.security.refresh-token-hours:12}")
    private long refreshTokenHours;

    public RefreshTokenService(RefreshTokenRepository refreshTokenRepository) {
        this.refreshTokenRepository = refreshTokenRepository;
    }

    public long getRefreshTokenMaxAgeSeconds() {
        return refreshTokenHours * 3600;
    }

    /** Cancels the user's old tokens, creates one new token, returns its text. */
    @Transactional
    public String rotateToken(User user) {
        Instant now = Instant.now();
        refreshTokenRepository.deleteByExpiresAtBefore(now);      // cleanup of old rows
        refreshTokenRepository.revokeActiveTokens(user, now);     // cancel the old tokens

        // Two random UUIDs joined = a long text nobody can guess.
        String token = UUID.randomUUID() + "." + UUID.randomUUID();
        refreshTokenRepository.save(new RefreshToken(
                token, user, Instant.now().plus(refreshTokenHours, ChronoUnit.HOURS)));
        return token;
    }

    /** Reads the refresh cookie, checks it, returns the user. Wrong token gives 401. */
    public User validateRefreshCookie(HttpServletRequest request) {
        String token = extractCookie(request, AuthCookieService.REFRESH_TOKEN_COOKIE);
        if (token == null || token.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Refresh token missing");
        }

        RefreshToken refreshToken = refreshTokenRepository.findByTokenAndRevokedFalse(token)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Refresh token invalid"));

        if (refreshToken.getExpiresAt().isBefore(Instant.now())) {
            refreshTokenRepository.revokeByToken(token);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Refresh token expired");
        }

        return refreshToken.getUser();
    }

    /** Logout: cancel the token that came with the request. */
    @Transactional
    public void revokeFromRequest(HttpServletRequest request) {
        String token = extractCookie(request, AuthCookieService.REFRESH_TOKEN_COOKIE);
        if (token == null || token.isBlank()) {
            return;
        }
        refreshTokenRepository.revokeByToken(token);
    }

    private String extractCookie(HttpServletRequest request, String cookieName) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        return Arrays.stream(cookies)
                .filter(cookie -> cookieName.equals(cookie.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .orElse(null);
    }
}
