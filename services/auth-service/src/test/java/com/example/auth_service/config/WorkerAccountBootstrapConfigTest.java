package com.example.auth_service.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.auth_service.entity.User;
import com.example.auth_service.repository.UserLoginRepository;
import com.example.auth_service.security.PasswordService;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Unit test of the worker service account bootstrap. The database is a fake; BCrypt is real. */
@ExtendWith(MockitoExtension.class)
class WorkerAccountBootstrapConfigTest {

    private static final String STRONG = "this-is-a-long-test-only-worker-password-123";

    @Mock
    private UserLoginRepository repository;

    private final PasswordService passwordService = new PasswordService();
    private WorkerAccountBootstrapConfig config;

    @BeforeEach
    void setUp() {
        config = new WorkerAccountBootstrapConfig(repository, passwordService);
    }

    @Test
    void workerIsNowAKnownRole() {
        assertThat(passwordService.normalizeRole(" worker ")).isEqualTo("WORKER");
    }

    @Test
    void offByDefaultDoesNothing() {
        config.ensure(false, "flexsim-worker", STRONG);

        verifyNoInteractions(repository);
    }

    @Test
    void aShortPasswordIsRefused() {
        config.ensure(true, "flexsim-worker", "short");

        verifyNoInteractions(repository);
    }

    @Test
    void createsTheAccountWithRoleWorkerAndAHashedPassword() {
        when(repository.findByUsernameIgnoreCase("flexsim-worker")).thenReturn(Optional.empty());

        config.ensure(true, " flexsim-worker ", STRONG);

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getUsername()).isEqualTo("flexsim-worker");
        assertThat(saved.getValue().getRole()).isEqualTo("WORKER");
        assertThat(saved.getValue().getPassword()).startsWith("$2").isNotEqualTo(STRONG);   // BCrypt hash, not the password
    }

    @Test
    void anExistingWorkerWithTheSamePasswordIsLeftAlone() {
        User worker = new User("flexsim-worker", passwordService.encodePassword(STRONG), "WORKER");
        when(repository.findByUsernameIgnoreCase("flexsim-worker")).thenReturn(Optional.of(worker));

        config.ensure(true, "flexsim-worker", STRONG);

        verify(repository, never()).save(any());
    }

    @Test
    void aNewPasswordInTheSettingsIsApplied() {
        User worker = new User("flexsim-worker", passwordService.encodePassword("the-old-long-worker-password-0000000"), "WORKER");
        when(repository.findByUsernameIgnoreCase("flexsim-worker")).thenReturn(Optional.of(worker));

        config.ensure(true, "flexsim-worker", STRONG);

        verify(repository).save(worker);
        assertThat(passwordService.matches(worker, STRONG)).isTrue();
    }

    @Test
    void aPersonsAccountIsNeverTurnedIntoAWorker() {
        User person = new User("flexsim-worker", passwordService.encodePassword("x"), "OPERATOR");
        when(repository.findByUsernameIgnoreCase("flexsim-worker")).thenReturn(Optional.of(person));

        config.ensure(true, "flexsim-worker", STRONG);

        verify(repository, never()).save(any());
        assertThat(person.getRole()).isEqualTo("OPERATOR");
    }
}
