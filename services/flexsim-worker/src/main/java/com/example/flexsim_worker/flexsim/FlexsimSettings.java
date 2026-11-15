package com.example.flexsim_worker.flexsim;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * All "app.flexsim.*" settings from application.properties, in one box.
 *
 *   exePath          the FlexSim program (flexsim.exe)
 *   modelPath        the model file (.fsm) that every run starts from
 *   workDir          each run gets its own folder here: run-<runId>
 *   maxRunMinutes    a run that is not finished after this time is stopped (FAILED)
 *   exitGraceSeconds after "finished", how long FlexSim may take to close by itself
 *   startupCheck     check at start-up that exePath and modelPath exist
 */
@ConfigurationProperties("app.flexsim")
public record FlexsimSettings(
        String exePath,
        String modelPath,
        String workDir,
        int maxRunMinutes,
        int exitGraceSeconds,
        boolean startupCheck) {
}
