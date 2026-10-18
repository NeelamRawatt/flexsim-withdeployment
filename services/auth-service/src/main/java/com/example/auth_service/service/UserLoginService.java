package com.example.auth_service.service;

import com.example.auth_service.dto.LoginErrorResponseDTO;
import com.example.auth_service.dto.UserLoginRequestDTO;
import com.example.auth_service.dto.UserLoginResponseDTO;
import com.example.auth_service.entity.User;
import com.example.auth_service.repository.UserLoginRepository;
import com.example.auth_service.security.AppUserPrincipal;
import com.example.auth_service.security.AuthCookieService;
import com.example.auth_service.security.PasswordService;
import com.example.auth_service.security.RefreshTokenService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * The LOGIN LOGIC. A service holds the real work; the controller only receives the request.
 *
 * Login, step by step:
 *  1. Check that username and password were sent.
 *  2. Find the user in the database.
 *  3. Check the password with BCrypt.
 *  4. Make a refresh token (saved in the database) and a short JWT token.
 *  5. Put both tokens in HttpOnly cookies.
 *  6. Write LOGIN_SUCCESS in the audit log.
 */
@Service
public class UserLoginService {

    private final UserLoginRepository userLoginRepository;
    private final PasswordService passwordService;
    private final RefreshTokenService refreshTokenService;
    private final AuthCookieService authCookieService;
    private final SecurityAuditService securityAuditService;

    public UserLoginService(UserLoginRepository userLoginRepository,
                            PasswordService passwordService,
                            RefreshTokenService refreshTokenService,
                            AuthCookieService authCookieService,
                            SecurityAuditService securityAuditService) {
        this.userLoginRepository = userLoginRepository;
        this.passwordService = passwordService;
        this.refreshTokenService = refreshTokenService;
        this.authCookieService = authCookieService;
        this.securityAuditService = securityAuditService;
    }

    public ResponseEntity<?> login(UserLoginRequestDTO request, HttpServletResponse response) {
        if (request.username() == null || request.username().isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(new LoginErrorResponseDTO("username", "USERNAME IS REQUIRED"));
        }

        if (request.password() == null) {
            return ResponseEntity.badRequest()
                    .body(new LoginErrorResponseDTO("password", "PASSWORD IS REQUIRED"));
        }

        User user = userLoginRepository.findByUsernameIgnoreCase(request.username()).orElse(null);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new LoginErrorResponseDTO("username", "Invalid Username"));
        }

        if (!passwordService.matches(user, request.password())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new LoginErrorResponseDTO("password", "Invalid Password"));
        }

        String refreshToken = refreshTokenService.rotateToken(user);
        response.addHeader("Set-Cookie", authCookieService.buildAccessTokenCookie(user).toString());
        response.addHeader("Set-Cookie", authCookieService.buildRefreshTokenCookie(refreshToken).toString());

        securityAuditService.record(user.getUsername(), "LOGIN_SUCCESS", "User authenticated successfully");

        return ResponseEntity.ok(new UserLoginResponseDTO(
                "Login successful", user.getUsername(), passwordService.normalizeRole(user.getRole())));
    }

    /** Uses the refresh cookie to give a new short token and a new refresh token. */
    public UserLoginResponseDTO refresh(HttpServletRequest request, HttpServletResponse response) {
        User user = refreshTokenService.validateRefreshCookie(request);
        String nextRefreshToken = refreshTokenService.rotateToken(user);

        response.addHeader("Set-Cookie", authCookieService.buildAccessTokenCookie(user).toString());
        response.addHeader("Set-Cookie", authCookieService.buildRefreshTokenCookie(nextRefreshToken).toString());

        return new UserLoginResponseDTO(
                "Session refreshed", user.getUsername(), passwordService.normalizeRole(user.getRole()));
    }

    /** Cancels the refresh token and tells the browser to delete both cookies. */
    public void logout(HttpServletRequest request, HttpServletResponse response, Authentication authentication) {
        refreshTokenService.revokeFromRequest(request);
        response.addHeader("Set-Cookie", authCookieService.clearAccessTokenCookie().toString());
        response.addHeader("Set-Cookie", authCookieService.clearRefreshTokenCookie().toString());

        if (authentication != null && authentication.getPrincipal() instanceof AppUserPrincipal principal) {
            securityAuditService.record(principal.getUsername(), "LOGOUT", "User logged out");
        }
    }

    /** "Who am I?" The React app calls this when it starts. */
    public UserLoginResponseDTO me(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof AppUserPrincipal principal)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized");
        }

        return new UserLoginResponseDTO("Authenticated", principal.getUsername(), principal.getRole());
    }
}
