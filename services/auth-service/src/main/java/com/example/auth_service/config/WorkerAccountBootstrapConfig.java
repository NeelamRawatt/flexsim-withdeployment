package com.example.auth_service.config;

import com.example.auth_service.entity.User;
import com.example.auth_service.repository.UserLoginRepository;
import com.example.auth_service.security.PasswordService;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Creates the SERVICE ACCOUNT of the flexsim-worker when auth-service starts.
 *
 * Service account = a login for a PROGRAM, not a person. The worker logs in with it
 * (POST /api/auth/login, like a person) and gets a normal token with role WORKER.
 * Other services allow WORKER only where the worker needs it
 * (example: POST /api/simulation/runs/{id}/status in simulation-service).
 *
 * SAFETY RULES:
 *  - OFF by default. Turn it on with APP_BOOTSTRAP_WORKER_ENABLED=true.
 *  - NO default password. The password comes from .env / Key Vault, and must be LONG
 *    (min. 32 characters): a program does not need to remember it, so it can be very strong.
 *  - If the password in the settings changes (rotation), the account gets the new password
 *    at the next start. So rotating = change the setting in BOTH auth-service and the worker, restart.
 *  - If a PERSON already has this username (different role), nothing is changed: we never
 *    turn a human account into a worker account, or the other way round.
 */
@Configuration(proxyBeanMethods = false)
public class WorkerAccountBootstrapConfig {

    private static final Logger log = LoggerFactory.getLogger(WorkerAccountBootstrapConfig.class);

    static final String WORKER_ROLE = "WORKER";
    static final int MIN_PASSWORD_LENGTH = 32;

    private final UserLoginRepository userLoginRepository;
    private final PasswordService passwordService;

    public WorkerAccountBootstrapConfig(UserLoginRepository userLoginRepository, PasswordService passwordService) {
        this.userLoginRepository = userLoginRepository;
        this.passwordService = passwordService;
    }

    @Bean
    public CommandLineRunner ensureWorkerAccount(
            @Value("${app.security.bootstrap.worker.enabled:false}") boolean enabled,
            @Value("${app.security.bootstrap.worker.username:flexsim-worker}") String username,
            @Value("${app.security.bootstrap.worker.password:}") String password
    ) {
        return args -> ensure(enabled, username, password);
    }

    /** The real work (separate method so a unit test can call it directly). */
    void ensure(boolean enabled, String username, String password) {
        if (!enabled) {
            return;
        }
        if (username == null || username.isBlank()) {
            log.warn("Worker account skipped: the username is blank");
            return;
        }
        if (password == null || password.length() < MIN_PASSWORD_LENGTH) {
            log.warn("Worker account skipped: the password must be at least {} characters", MIN_PASSWORD_LENGTH);
            return;
        }
        String name = username.trim();

        Optional<User> existing = userLoginRepository.findByUsernameIgnoreCase(name);
        if (existing.isEmpty()) {
            userLoginRepository.save(new User(name, passwordService.encodePassword(password), WORKER_ROLE));
            log.info("Worker service account created: {}", name);
            return;
        }

        User user = existing.get();
        if (!WORKER_ROLE.equals(passwordService.normalizeRole(user.getRole()))) {
            log.error("Worker account NOT created: the username '{}' already belongs to a {} user. "
                    + "Choose another APP_BOOTSTRAP_WORKER_USERNAME.", name, user.getRole());
            return;
        }
        if (!passwordService.matches(user, password)) {
            user.setPassword(passwordService.encodePassword(password));
            userLoginRepository.save(user);
            log.info("Worker service account password updated (rotation): {}", name);
        }
    }
}
