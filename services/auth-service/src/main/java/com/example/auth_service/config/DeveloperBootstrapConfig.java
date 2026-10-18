package com.example.auth_service.config;

import com.example.auth_service.entity.User;
import com.example.auth_service.repository.UserLoginRepository;
import com.example.auth_service.security.PasswordService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Creates the very first user ("developer") when the service starts, so somebody
 * can log in and then create the other users.
 *
 * SAFETY RULES:
 *  - It is OFF by default (enabled=false).
 *  - There is NO default password. If you turn it on, you must give the password
 *    in .env (APP_BOOTSTRAP_DEVELOPER_PASSWORD).
 *  - In production: turn it on only for the first start, create the first real users,
 *    then turn it off again.
 */
@Configuration(proxyBeanMethods = false)
public class DeveloperBootstrapConfig {

    private static final Logger log = LoggerFactory.getLogger(DeveloperBootstrapConfig.class);

    private final UserLoginRepository userLoginRepository;
    private final PasswordService passwordService;

    public DeveloperBootstrapConfig(UserLoginRepository userLoginRepository, PasswordService passwordService) {
        this.userLoginRepository = userLoginRepository;
        this.passwordService = passwordService;
    }

    // A CommandLineRunner runs ONE time, right after the service has started.
    @Bean
    public CommandLineRunner ensureDeveloperUser(
            @Value("${app.security.bootstrap.developer.enabled:false}") boolean enabled,
            @Value("${app.security.bootstrap.developer.username:developer}") String username,
            @Value("${app.security.bootstrap.developer.password:}") String password
    ) {
        return args -> {
            if (!enabled) {
                return;
            }

            if (username == null || username.trim().isBlank() || password == null || password.isBlank()) {
                log.warn("Developer bootstrap skipped: username or password is blank");
                return;
            }

            // If this user already exists, do nothing (safe to restart many times).
            if (userLoginRepository.existsByUsernameIgnoreCase(username.trim())) {
                return;
            }

            User user = new User(username.trim(), passwordService.encodePassword(password), "DEVELOPER");
            userLoginRepository.save(user);
            log.info("Bootstrap developer user created: {}", username.trim());
        };
    }
}
