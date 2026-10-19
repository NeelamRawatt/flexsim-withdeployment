package com.example.auth_service.service;

import com.example.auth_service.dto.ChangeOwnPasswordRequestDTO;
import com.example.auth_service.dto.CreateManagedUserRequestDTO;
import com.example.auth_service.dto.ManagedUserResponseDTO;
import com.example.auth_service.dto.UpdateManagedUserPasswordRequestDTO;
import com.example.auth_service.entity.User;
import com.example.auth_service.repository.RefreshTokenRepository;
import com.example.auth_service.repository.UserLoginRepository;
import com.example.auth_service.security.AppUserPrincipal;
import com.example.auth_service.security.PasswordService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests the BUSINESS RULES of user management:
 *  - Only a DEVELOPER may create an ADMIN, and only one ADMIN may exist.
 *  - Usernames must be unique.
 *  - Only OPERATOR and VIEWER users can be deleted.
 *
 * The database and the audit log are fakes (mocks), so these tests are fast.
 */
@ExtendWith(MockitoExtension.class)
class UserManagementServiceTest {

    @Mock
    private UserLoginRepository userLoginRepository;
    @Mock
    private SecurityAuditService securityAuditService;
    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    private UserManagementService service;
    private PasswordService passwordService;

    @BeforeEach
    void setUp() {
        // PasswordService is real (it only needs the fake repository).
        passwordService = new PasswordService();
        service = new UserManagementService(userLoginRepository, passwordService, securityAuditService, refreshTokenRepository);
    }

    private User user(Long id, String username, String role) {
        User user = new User(username, "hash", role);
        user.setId(id);
        return user;
    }

    /** Builds a "logged-in person" like Spring Security would give us. */
    private Authentication loggedInAs(String username, String role) {
        User user = user(1L, username, role);
        AppUserPrincipal principal = new AppUserPrincipal(user, role);
        return new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
    }

    private CreateManagedUserRequestDTO request(String username, String role) {
        return new CreateManagedUserRequestDTO(username, "Pass@123", role);
    }

    @Test
    @DisplayName("an ADMIN can create an OPERATOR")
    void adminCreatesOperator() {
        when(userLoginRepository.existsByUsernameIgnoreCase("bob")).thenReturn(false);
        when(userLoginRepository.save(any(User.class))).thenAnswer(invocation -> {
            User saved = invocation.getArgument(0);
            saved.setId(10L);               // pretend the database gave id 10
            return saved;
        });

        ManagedUserResponseDTO result = service.createManagedUser(loggedInAs("admin1", "ADMIN"), request("bob", "operator"));

        assertThat(result.id()).isEqualTo(10L);
        assertThat(result.username()).isEqualTo("bob");
        assertThat(result.role()).isEqualTo("OPERATOR");
        verify(securityAuditService).record(eq("admin1"), eq("CREATE_USER"), any(String.class));
    }

    @Test
    @DisplayName("an ADMIN can NOT create another ADMIN")
    void adminCannotCreateAdmin() {
        assertThatThrownBy(() -> service.createManagedUser(loggedInAs("admin1", "ADMIN"), request("boss", "ADMIN")))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value()).isEqualTo(400));

        verify(userLoginRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("a DEVELOPER can NOT create a second ADMIN")
    void onlyOneAdminAllowed() {
        when(userLoginRepository.existsByRoleIgnoreCase("ADMIN")).thenReturn(true);

        assertThatThrownBy(() -> service.createManagedUser(loggedInAs("dev", "DEVELOPER"), request("boss2", "ADMIN")))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value()).isEqualTo(409));
    }

    @Test
    @DisplayName("a duplicate username is rejected with 409 Conflict")
    void duplicateUsernameIsRejected() {
        when(userLoginRepository.existsByUsernameIgnoreCase("bob")).thenReturn(true);

        assertThatThrownBy(() -> service.createManagedUser(loggedInAs("admin1", "ADMIN"), request("bob", "VIEWER")))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value()).isEqualTo(409));

        verify(userLoginRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("missing username, password or role gives 400 Bad Request")
    void missingFieldsGiveBadRequest() {
        CreateManagedUserRequestDTO noPassword = new CreateManagedUserRequestDTO("bob", null, "VIEWER");

        assertThatThrownBy(() -> service.createManagedUser(loggedInAs("admin1", "ADMIN"), noPassword))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value()).isEqualTo(400));
    }

    @Test
    @DisplayName("an OPERATOR user can be deleted")
    void operatorCanBeDeleted() {
        User operator = user(5L, "bob", "OPERATOR");
        when(userLoginRepository.findById(5L)).thenReturn(Optional.of(operator));

        service.deleteManagedUser(loggedInAs("admin1", "ADMIN"), 5L);

        verify(userLoginRepository).delete(operator);
        verify(securityAuditService).record(eq("admin1"), eq("DELETE_USER"), any(String.class));
    }

    @Test
    @DisplayName("an ADMIN user can NOT be deleted")
    void adminCannotBeDeleted() {
        User admin = user(2L, "boss", "ADMIN");
        when(userLoginRepository.findById(2L)).thenReturn(Optional.of(admin));

        assertThatThrownBy(() -> service.deleteManagedUser(loggedInAs("dev", "DEVELOPER"), 2L))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value()).isEqualTo(400));

        verify(userLoginRepository, never()).delete(any(User.class));
    }

    @Test
    @DisplayName("deleting an unknown user gives 404 Not Found")
    void unknownUserGives404() {
        when(userLoginRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteManagedUser(loggedInAs("admin1", "ADMIN"), 99L))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value()).isEqualTo(404));
    }

    @Test
    @DisplayName("without a login (no principal) the service answers 401")
    void noLoginGives401() {
        assertThatThrownBy(() -> service.createManagedUser(null, request("bob", "VIEWER")))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value()).isEqualTo(401));
    }

    @Test
    @DisplayName("changing your own password cancels all your refresh tokens")
    void changeOwnPasswordRevokesRefreshTokens() {
        User me = new User("bob", passwordService.encodePassword("OldPass@1"), "OPERATOR");
        me.setId(7L);
        when(userLoginRepository.findByUsernameIgnoreCase("bob")).thenReturn(Optional.of(me));

        service.changeOwnPassword(loggedInAs("bob", "OPERATOR"),
                new ChangeOwnPasswordRequestDTO("OldPass@1", "NewPass@1"));

        verify(userLoginRepository).save(me);
        verify(refreshTokenRepository).revokeActiveTokens(eq(me), any(Instant.class));
        assertThat(passwordService.matches(me, "NewPass@1")).isTrue();
    }

    @Test
    @DisplayName("a wrong current password changes nothing and cancels no tokens")
    void wrongCurrentPasswordRevokesNothing() {
        User me = new User("bob", passwordService.encodePassword("OldPass@1"), "OPERATOR");
        me.setId(7L);
        when(userLoginRepository.findByUsernameIgnoreCase("bob")).thenReturn(Optional.of(me));

        assertThatThrownBy(() -> service.changeOwnPassword(loggedInAs("bob", "OPERATOR"),
                new ChangeOwnPasswordRequestDTO("WrongPass", "NewPass@1")))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value()).isEqualTo(400));

        verify(userLoginRepository, never()).save(any(User.class));
        verify(refreshTokenRepository, never()).revokeActiveTokens(any(User.class), any(Instant.class));
    }

    @Test
    @DisplayName("an admin resetting a user's password cancels that user's refresh tokens")
    void resetPasswordRevokesRefreshTokens() {
        User operator = user(5L, "bob", "OPERATOR");
        when(userLoginRepository.findById(5L)).thenReturn(Optional.of(operator));

        service.updateManagedUserPassword(loggedInAs("admin1", "ADMIN"), 5L,
                new UpdateManagedUserPasswordRequestDTO("Reset@123"));

        verify(userLoginRepository).save(operator);
        verify(refreshTokenRepository).revokeActiveTokens(eq(operator), any(Instant.class));
        verify(securityAuditService).record(eq("admin1"), eq("RESET_USER_PASSWORD"), any(String.class));
    }
}