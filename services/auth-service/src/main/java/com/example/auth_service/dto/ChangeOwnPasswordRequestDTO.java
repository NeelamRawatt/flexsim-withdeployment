package com.example.auth_service.dto;

/** JSON box for a user to change his OWN password: the current one and the new one. */
public record ChangeOwnPasswordRequestDTO(String currentPassword, String newPassword) {
}
