package com.example.auth_service.service;

import com.example.auth_service.dto.ChangeOwnPasswordRequestDTO;
import com.example.auth_service.dto.CreateManagedUserRequestDTO;
import com.example.auth_service.dto.ManagedUserResponseDTO;
import com.example.auth_service.dto.UpdateManagedUserPasswordRequestDTO;
import com.example.auth_service.dto.UpdateManagedUserRoleRequestDTO;
import com.example.auth_service.entity.User;
import com.example.auth_service.repository.RefreshTokenRepository;
import com.example.auth_service.repository.UserLoginRepository;
import com.example.auth_service.security.AppUserPrincipal;
import com.example.auth_service.security.PasswordService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * The RULES for managing users:
 *  - only a DEVELOPER may create an ADMIN, and only one ADMIN can exist;
 *  - usernames must be unique;
 *  - only OPERATOR and VIEWER users can be deleted or get a new role.
 * These rules are tested in UserManagementServiceTest.
 *
 * SECURITY RULE (added in the "fix it" step): when a password is changed or reset,
 * ALL refresh tokens of that user are cancelled in the same database transaction.
 * Why: a refresh token that was stolen before the password change must stop working.
 *
 * ResponseStatusException = how we send an error with a status code:
 *   400 Bad Request (wrong input), 401 not logged in, 404 not found, 409 Conflict (already exists).
 */
@Service
public class UserManagementService {

    private static final Set<String> LISTABLE_ROLES = Set.of("ADMIN", "OPERATOR", "VIEWER");
    private static final Set<String> EDITABLE_ROLES = Set.of("OPERATOR", "VIEWER");

    private final UserLoginRepository userLoginRepository;
    private final PasswordService passwordService;
    private final SecurityAuditService securityAuditService;
    private final RefreshTokenRepository refreshTokenRepository;

    public UserManagementService(UserLoginRepository userLoginRepository,
                                 PasswordService passwordService,
                                 SecurityAuditService securityAuditService,
                                 RefreshTokenRepository refreshTokenRepository) {
        this.userLoginRepository = userLoginRepository;
        this.passwordService = passwordService;
        this.securityAuditService = securityAuditService;
        this.refreshTokenRepository = refreshTokenRepository;
    }

    // @Transactional = the new password and the token cancel are saved together, or not at all.
    @Transactional
    public void changeOwnPassword(Authentication authentication, ChangeOwnPasswordRequestDTO request) {
        AppUserPrincipal principal = requirePrincipal(authentication);

        if (request == null || isBlank(request.currentPassword()) || isBlank(request.newPassword())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Current and new passwords are required");
        }

        User user = userLoginRepository.findByUsernameIgnoreCase(principal.getUsername())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        if (!passwordService.matches(user, request.currentPassword())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Current password is incorrect");
        }

        user.setPassword(passwordService.encodePassword(request.newPassword()));
        userLoginRepository.save(user);
        // Cancel every active refresh token of this user (old sessions must log in again).
        refreshTokenRepository.revokeActiveTokens(user, Instant.now());
        securityAuditService.record(user.getUsername(), "PASSWORD_CHANGE", "Changed own password");
    }

    public ManagedUserResponseDTO createManagedUser(Authentication authentication, CreateManagedUserRequestDTO request) {
        AppUserPrincipal principal = requirePrincipal(authentication);

        if (request == null || isBlank(request.username()) || isBlank(request.password()) || isBlank(request.role())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Username, password and role are required");
        }

        String normalizedRole = passwordService.normalizeRole(request.role());
        String currentRole = principal.getRole();

        if ("ADMIN".equals(normalizedRole)) {
            if (!"DEVELOPER".equals(currentRole)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only developers can create admin users");
            }

            if (userLoginRepository.existsByRoleIgnoreCase("ADMIN")) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "An admin user already exists");
            }
        } else if (!EDITABLE_ROLES.contains(normalizedRole)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only ADMIN, OPERATOR or VIEWER users can be created");
        }

        if (userLoginRepository.existsByUsernameIgnoreCase(request.username())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Username already exists");
        }

        User user = new User(
                request.username().trim(),
                passwordService.encodePassword(request.password()),
                normalizedRole);

        User saved = userLoginRepository.save(user);
        securityAuditService.record(principal.getUsername(), "CREATE_USER",
                "Created user " + saved.getUsername() + " with role " + normalizedRole);

        return toDto(saved);
    }

    public List<ManagedUserResponseDTO> listManagedUsers() {
        return userLoginRepository.findByRoleInOrderByUsernameAsc(LISTABLE_ROLES)
                .stream()
                .map(this::toDto)
                .toList();
    }

    public ManagedUserResponseDTO updateManagedUserRole(
            Authentication authentication,
            Long userId,
            UpdateManagedUserRoleRequestDTO request
    ) {
        AppUserPrincipal principal = requirePrincipal(authentication);

        if (request == null || isBlank(request.role())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Role is required");
        }

        User user = userLoginRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        String currentRole = passwordService.normalizeRole(user.getRole());
        if (!LISTABLE_ROLES.contains(currentRole)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only ADMIN, OPERATOR and VIEWER users can be updated here");
        }

        String normalizedRole = passwordService.normalizeRole(request.role());
        if (!EDITABLE_ROLES.contains(normalizedRole)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Role must be OPERATOR or VIEWER");
        }

        user.setRole(normalizedRole);
        User saved = userLoginRepository.save(user);

        securityAuditService.record(principal.getUsername(), "UPDATE_USER_ROLE",
                "Updated role for " + saved.getUsername() + " to " + normalizedRole);

        return toDto(saved);
    }

    @Transactional
    public void updateManagedUserPassword(
            Authentication authentication,
            Long userId,
            UpdateManagedUserPasswordRequestDTO request
    ) {
        AppUserPrincipal principal = requirePrincipal(authentication);

        if (request == null || isBlank(request.newPassword())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "New password is required");
        }

        User user = userLoginRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        String currentRole = passwordService.normalizeRole(user.getRole());
        if (!LISTABLE_ROLES.contains(currentRole)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only ADMIN, OPERATOR and VIEWER users can be updated here");
        }

        user.setPassword(passwordService.encodePassword(request.newPassword()));
        userLoginRepository.save(user);
        // Cancel every active refresh token of this user, so a stolen one stops working.
        refreshTokenRepository.revokeActiveTokens(user, Instant.now());

        securityAuditService.record(principal.getUsername(), "RESET_USER_PASSWORD",
                "Reset password for " + user.getUsername());
    }

    public void deleteManagedUser(Authentication authentication, Long userId) {
        AppUserPrincipal principal = requirePrincipal(authentication);

        User user = userLoginRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        String currentRole = passwordService.normalizeRole(user.getRole());
        if (!EDITABLE_ROLES.contains(currentRole)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only OPERATOR and VIEWER users can be deleted here");
        }

        // The refresh tokens of this user are deleted by the database (ON DELETE CASCADE in V1).
        userLoginRepository.delete(user);

        securityAuditService.record(principal.getUsername(), "DELETE_USER",
                "Deleted user " + user.getUsername() + " with role " + currentRole);
    }

    private AppUserPrincipal requirePrincipal(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof AppUserPrincipal principal)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized");
        }
        return principal;
    }

    private ManagedUserResponseDTO toDto(User user) {
        return new ManagedUserResponseDTO(user.getId(), user.getUsername(), passwordService.normalizeRole(user.getRole()));
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isBlank();
    }
}