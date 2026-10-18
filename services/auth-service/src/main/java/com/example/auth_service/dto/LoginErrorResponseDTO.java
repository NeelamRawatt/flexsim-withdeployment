package com.example.auth_service.dto;

/**
 * The JSON answer when login fails: which field is wrong, and a message.
 *     {"field": "password", "message": "Invalid Password"}
 *
 * Note (kept from the old app so the React app keeps working): "Invalid Username" and
 * "Invalid Password" are different messages. This helps users, but it also tells an
 * attacker which usernames exist. We will decide later if we make it one message.
 */
public record LoginErrorResponseDTO(String field, String message) {
}
