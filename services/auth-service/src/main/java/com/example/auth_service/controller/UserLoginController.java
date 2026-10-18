package com.example.auth_service.controller;

import com.example.auth_service.dto.UserLoginRequestDTO;
import com.example.auth_service.dto.UserLoginResponseDTO;
import com.example.auth_service.service.UserLoginService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The "doors" of the service for login. A controller only RECEIVES a web request
 * and hands it to a service.
 *
 *   POST /api/auth/login    -> log in (sets two cookies)
 *   POST /api/auth/refresh  -> new short token, using the refresh cookie
 *   POST /api/auth/logout   -> log out (cancels the refresh token, clears the cookies)
 *   GET  /api/auth/me       -> "who am I?" (the React app asks this at start-up)
 */
@RestController
@RequestMapping("/api/auth")
public class UserLoginController {

    private final UserLoginService userLoginService;

    public UserLoginController(UserLoginService userLoginService) {
        this.userLoginService = userLoginService;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody UserLoginRequestDTO request, HttpServletResponse response) {
        return userLoginService.login(request, response);
    }

    @PostMapping("/refresh")
    public ResponseEntity<UserLoginResponseDTO> refresh(HttpServletRequest request, HttpServletResponse response) {
        return ResponseEntity.ok(userLoginService.refresh(request, response));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response, Authentication authentication) {
        userLoginService.logout(request, response, authentication);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public ResponseEntity<UserLoginResponseDTO> me(Authentication authentication) {
        return ResponseEntity.ok(userLoginService.me(authentication));
    }
}
