package com.example.auth_service.security;

import com.example.auth_service.entity.User;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Locale;

/**
 * Everything about passwords.
 *
 * BCrypt = a ONE-WAY scrambler. It turns "Dev@12345" into a long text like
 * "$2a$10$N9qo8uLOickgx2ZMRZoMye...". You can NOT turn the text back into the
 * password. To check a login, BCrypt scrambles the typed password again and
 * compares the two scrambled texts.
 * Why: if a hacker steals the database, he gets only scrambled texts, not passwords.
 *
 * PRODUCTION CHANGES from the old monolith code:
 *  1. The old code accepted a plain-text password stored in the database and then
 *     upgraded it. We REMOVED that. A password that is not a BCrypt hash never matches.
 *     (If your old data has plain-text passwords, we hash them ONCE during the data copy.)
 *  2. A missing role now becomes VIEWER (can only look), not ADMIN (can do everything).
 *     This is called "least privilege": give the smallest power by default.
 */
@Service
public class PasswordService {

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    /** true if the typed password is the right one for this user. */
    public boolean matches(User user, String rawPassword) {
        String stored = user.getPassword();
        if (stored == null || rawPassword == null || !isBcryptHash(stored)) {
            return false;
        }
        return passwordEncoder.matches(rawPassword, stored);
    }

    /** Turns a real password into a BCrypt hash. Use this before saving a password. */
    public String encodePassword(String rawPassword) {
        return passwordEncoder.encode(rawPassword);
    }

    /** Allowed roles: ADMIN, DEVELOPER, OPERATOR, VIEWER. Anything else becomes VIEWER. */
    public String normalizeRole(String role) {
        if (role == null || role.isBlank()) {
            return "VIEWER";
        }
        String normalized = role.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "ADMIN", "DEVELOPER", "OPERATOR", "VIEWER" -> normalized;
            default -> "VIEWER";
        };
    }

    private boolean isBcryptHash(String value) {
        return value.startsWith("$2a$") || value.startsWith("$2b$") || value.startsWith("$2y$");
    }
}
