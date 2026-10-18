package com.example.auth_service.dto;

/** JSON box for an admin to reset another user's password. */
public record UpdateManagedUserPasswordRequestDTO(String newPassword) {
}
