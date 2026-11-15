package com.example.flexsim_worker.startup;

import com.example.flexsim_worker.auth.WorkerLoginException;
import com.example.flexsim_worker.auth.WorkerTokenProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Runs ONCE when the worker starts: "can I log in?"
 *
 * Why: without this check, a wrong password is found only when the first job arrives,
 * maybe hours later, at night. With it, the problem is visible in the first second.
 *
 *   wrong password / not a WORKER account  -> the worker STOPS with a clear error (fail fast).
 *                                             A person must fix the settings.
 *   auth-service is down right now          -> only a WARNING; the worker keeps running
 *                                             and logs in later, when the first job arrives.
 *                                             (Services often start in a different order.)
 */
@Component
@ConditionalOnProperty(name = "app.worker.startup-login-check", havingValue = "true", matchIfMissing = true)
public class StartupLoginCheck implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(StartupLoginCheck.class);

    private final WorkerTokenProvider tokenProvider;

    public StartupLoginCheck(WorkerTokenProvider tokenProvider) {
        this.tokenProvider = tokenProvider;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            tokenProvider.getToken();
            log.info("Start-up login check OK.");
        } catch (WorkerLoginException e) {
            if (e.isConfigProblem()) {
                log.error("Start-up login check FAILED: {}", e.getMessage());
                throw e;   // stop the worker
            }
            log.warn("Start-up login check: {}. The worker will try again when the first job arrives.", e.getMessage());
        }
    }
}
