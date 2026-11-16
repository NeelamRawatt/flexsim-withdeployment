package com.example.kpi_service.security;

/**
 * "Who is calling?" for ONE request, filled from the JWT token (no database call).
 * Same as in the other services.
 *
 *  userId   = the "uid" claim
 *  username = the token "subject"
 *  role     = ADMIN, DEVELOPER, OPERATOR, VIEWER or WORKER (without the ROLE_ prefix)
 */
public record AuthenticatedUser(Long userId, String username, String role) {
}
