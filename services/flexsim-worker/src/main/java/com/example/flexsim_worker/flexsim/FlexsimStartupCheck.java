package com.example.flexsim_worker.flexsim;

import java.nio.file.Files;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Runs ONCE when the worker starts: "is FlexSim really here?"
 *
 * Without this check, a wrong path is found only when a user clicks "Run",
 * and EVERY run fails. With it, the worker refuses to start (fail fast) and the log says why.
 */
@Component
@ConditionalOnProperty(name = "app.flexsim.startup-check", havingValue = "true", matchIfMissing = true)
public class FlexsimStartupCheck implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(FlexsimStartupCheck.class);

    private final FlexsimSettings settings;

    public FlexsimStartupCheck(FlexsimSettings settings) {
        this.settings = settings;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        Path exe = Path.of(settings.exePath());
        if (!Files.isRegularFile(exe)) {
            throw new IllegalStateException("FlexSim program not found: " + exe + " (check FLEXSIM_EXE_PATH)");
        }
        Path model = Path.of(settings.modelPath());
        if (!Files.isRegularFile(model)) {
            throw new IllegalStateException("FlexSim model not found: " + model + " (check FLEXSIM_MODEL_PATH)");
        }
        Path workDir = Path.of(settings.workDir()).toAbsolutePath().normalize();
        Files.createDirectories(workDir);
        log.info("FlexSim check OK. Program: {}, model: {}, run folders in: {}", exe, model, workDir);
    }
}
