package com.example.auth_service.dto;

/**
 * The JSON answer after login / refresh / me, and for simple messages:
 *     {"message": "Login successful", "username": "developer", "role": "DEVELOPER"}
 * A field that has no value is sent as null.
 */
public record UserLoginResponseDTO(String message, String username, String role) {

    /** Shortcut for answers that carry only a message. */
    public static UserLoginResponseDTO message(String message) {
        return new UserLoginResponseDTO(message, null, null);
    }
}
