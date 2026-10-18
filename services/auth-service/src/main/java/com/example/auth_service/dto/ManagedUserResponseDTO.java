package com.example.auth_service.dto;

/**
 * The JSON answer for one user: id, username, role.
 * The password (even scrambled) is NEVER sent out.
 */
public record ManagedUserResponseDTO(Long id, String username, String role) {
}
