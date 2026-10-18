package com.example.auth_service.dto;

/**
 * DTO = Data Transfer Object: a plain box for data that goes IN or OUT of the API.
 * This one is the JSON the React app sends to log in:
 *     {"username": "developer", "password": "Dev@12345"}
 *
 * "record" is a short way to write such a box in Java (version 16 and newer).
 * Java makes the constructor and the getters for us. To read a value you write
 * request.username() (not getUsername()).
 */
public record UserLoginRequestDTO(String username, String password) {
}
