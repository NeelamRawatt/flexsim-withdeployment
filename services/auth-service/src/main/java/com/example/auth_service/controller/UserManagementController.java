package com.example.auth_service.controller;

import com.example.auth_service.dto.ChangeOwnPasswordRequestDTO;
import com.example.auth_service.dto.CreateManagedUserRequestDTO;
import com.example.auth_service.dto.ManagedUserResponseDTO;
import com.example.auth_service.dto.UpdateManagedUserPasswordRequestDTO;
import com.example.auth_service.dto.UpdateManagedUserRoleRequestDTO;
import com.example.auth_service.dto.UserLoginResponseDTO;
import com.example.auth_service.service.UserManagementService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * The "doors" for managing users (/api/users).
 *
 * @PreAuthorize("hasAnyRole('ADMIN','DEVELOPER')") means: only people with one of these
 * roles may enter this door. A logged-in person with another role gets 403 Forbidden.
 * (Changing your OWN password has no role rule: every logged-in user may do it.)
 */
@RestController
@RequestMapping("/api/users")
public class UserManagementController {

    private final UserManagementService userManagementService;

    public UserManagementController(UserManagementService userManagementService) {
        this.userManagementService = userManagementService;
    }

    @PutMapping("/me/password")
    public ResponseEntity<UserLoginResponseDTO> changeOwnPassword(
            @RequestBody ChangeOwnPasswordRequestDTO request,
            Authentication authentication
    ) {
        userManagementService.changeOwnPassword(authentication, request);
        return ResponseEntity.ok(UserLoginResponseDTO.message("Password updated successfully"));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','DEVELOPER')")
    public ResponseEntity<ManagedUserResponseDTO> createUser(
            @RequestBody CreateManagedUserRequestDTO request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(userManagementService.createManagedUser(authentication, request));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','DEVELOPER')")
    public ResponseEntity<List<ManagedUserResponseDTO>> listManagedUsers() {
        return ResponseEntity.ok(userManagementService.listManagedUsers());
    }

    @PutMapping("/{userId}/role")
    @PreAuthorize("hasAnyRole('ADMIN','DEVELOPER')")
    public ResponseEntity<ManagedUserResponseDTO> updateManagedUserRole(
            @PathVariable Long userId,
            @RequestBody UpdateManagedUserRoleRequestDTO request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(userManagementService.updateManagedUserRole(authentication, userId, request));
    }

    @PutMapping("/{userId}/password")
    @PreAuthorize("hasAnyRole('ADMIN','DEVELOPER')")
    public ResponseEntity<UserLoginResponseDTO> updateManagedUserPassword(
            @PathVariable Long userId,
            @RequestBody UpdateManagedUserPasswordRequestDTO request,
            Authentication authentication
    ) {
        userManagementService.updateManagedUserPassword(authentication, userId, request);
        return ResponseEntity.ok(UserLoginResponseDTO.message("User password updated successfully"));
    }

    @DeleteMapping("/{userId}")
    @PreAuthorize("hasAnyRole('ADMIN','DEVELOPER')")
    public ResponseEntity<UserLoginResponseDTO> deleteManagedUser(
            @PathVariable Long userId,
            Authentication authentication
    ) {
        userManagementService.deleteManagedUser(authentication, userId);
        return ResponseEntity.ok(UserLoginResponseDTO.message("User deleted successfully"));
    }
}
