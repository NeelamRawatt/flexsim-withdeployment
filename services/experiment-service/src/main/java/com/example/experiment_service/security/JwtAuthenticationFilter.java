package com.example.experiment_service.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * A "security guard" at the door. It runs on EVERY request, before the controller.
 *
 * What it does:
 *  1. Looks for the token: header "Authorization: Bearer ..." or the cookie flexsim_access_token.
 *  2. Asks JwtTokenVerifier if the token is real.
 *  3. If yes, it marks the request as "logged in as <username>, role <ROLE>".
 *  4. If no token or a bad token, it marks nothing. The rules in SecurityConfig then answer 401.
 *
 * The guard NEVER blocks by itself. It only says who you are. SecurityConfig decides who may enter.
 * Difference from auth-service: no database call here. The role comes from the token
 * (so a role change is seen after the token expires, up to 10 minutes).
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    // Same cookie name as auth-service.
    public static final String ACCESS_TOKEN_COOKIE = "flexsim_access_token";

    private final JwtTokenVerifier tokenVerifier;

    public JwtAuthenticationFilter(JwtTokenVerifier tokenVerifier) {
        this.tokenVerifier = tokenVerifier;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String token = resolveToken(request);

        if (token != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            Optional<AuthenticatedUser> user = tokenVerifier.verify(token);
            if (user.isPresent()) {
                AuthenticatedUser principal = user.get();
                // Spring expects the prefix ROLE_ (hasRole('ADMIN') looks for ROLE_ADMIN).
                var authentication = new UsernamePasswordAuthenticationToken(
                        principal,
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + principal.role())));
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        }

        filterChain.doFilter(request, response);
    }

    private String resolveToken(HttpServletRequest request) {
        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (ACCESS_TOKEN_COOKIE.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }
}