package com.example.file_service.security;

/**
 * "Who is calling?" for ONE request.
 *
 * This small box is filled from the JWT token. We do NOT read the database for it.
 * auth-service signed the token, so we can trust what is inside (if the signature is good).
 *
 * A "record" is a short way to write a data box in Java. Java makes the constructor and
 * the readers for us. To read a value we write user.username(), not getUsername().
 *
 *  userId   = the "uid" claim (the id number in auth_db). Saved in uploaded_file.uploaded_by_user_id.
 *  username = the token "subject". Saved in uploaded_file.uploaded_by.
 *  role     = ADMIN, DEVELOPER, OPERATOR or VIEWER (without the ROLE_ prefix).
 */
public record AuthenticatedUser(Long userId, String username, String role) {
}