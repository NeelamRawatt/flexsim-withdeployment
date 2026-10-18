package com.example.auth_service.dto;

/** JSON box to create a user: username, password, role. */
public record CreateManagedUserRequestDTO(String username, String password, String role) {
}
