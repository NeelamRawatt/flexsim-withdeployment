package com.example.auth_service.security;

import com.example.auth_service.entity.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

/**
 * Builds the two login cookies. A cookie is a small piece of data the browser keeps
 * and sends back with every request to our site.
 *
 *  - HttpOnly     : JavaScript in the page cannot read the cookie, so a hacker script
 *                   cannot steal it (this protects against "XSS").
 *  - SameSite=Lax : the browser does not send it on most requests started by other sites.
 *  - Secure       : the cookie travels only over HTTPS.
 *                   In production set APP_COOKIE_SECURE=true.
 */
@Service
public class AuthCookieService {

    public static final String ACCESS_TOKEN_COOKIE = "flexsim_access_token";
    public static final String REFRESH_TOKEN_COOKIE = "flexsim_refresh_token";

    private final AppUserDetailsService appUserDetailsService;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;

    @Value("${app.security.cookie.secure:false}")
    private boolean secureCookie;

    public AuthCookieService(AppUserDetailsService appUserDetailsService,
                             JwtService jwtService,
                             RefreshTokenService refreshTokenService) {
        this.appUserDetailsService = appUserDetailsService;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
    }

    public ResponseCookie buildAccessTokenCookie(User user) {
        AppUserPrincipal principal = (AppUserPrincipal) appUserDetailsService.loadUserByUsername(user.getUsername());
        String token = jwtService.generateAccessToken(principal);

        return ResponseCookie.from(ACCESS_TOKEN_COOKIE, token)
                .httpOnly(true)
                .secure(secureCookie)
                .sameSite("Lax")
                .path("/")
                .maxAge(jwtService.getAccessTokenMaxAgeSeconds())
                .build();
    }

    public ResponseCookie buildRefreshTokenCookie(String token) {
        return ResponseCookie.from(REFRESH_TOKEN_COOKIE, token)
                .httpOnly(true)
                .secure(secureCookie)
                .sameSite("Lax")
                .path("/")
                .maxAge(refreshTokenService.getRefreshTokenMaxAgeSeconds())
                .build();
    }

    public ResponseCookie clearAccessTokenCookie() {
        return clearCookie(ACCESS_TOKEN_COOKIE);
    }

    public ResponseCookie clearRefreshTokenCookie() {
        return clearCookie(REFRESH_TOKEN_COOKIE);
    }

    // maxAge(0) tells the browser: delete this cookie now.
    private ResponseCookie clearCookie(String name) {
        return ResponseCookie.from(name, "")
                .httpOnly(true)
                .secure(secureCookie)
                .sameSite("Lax")
                .path("/")
                .maxAge(0)
                .build();
    }
}
