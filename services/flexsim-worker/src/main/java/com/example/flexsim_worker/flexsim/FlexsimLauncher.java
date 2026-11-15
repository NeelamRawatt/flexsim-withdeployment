package com.example.flexsim_worker.flexsim;

import com.example.flexsim_worker.event.SimulationTriggerRequestedEvent;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Prepares the files of a run and STARTS FlexSim with ProcessBuilder.
 *
 * ProcessBuilder = Java's way to start another program, like double-clicking it,
 * but with exact arguments. Example of the command we build:
 *
 *   flexsim.exe  C:\...\run-7\model.fsm  /maintenance nogui_disablemsg_runscript  /scriptpath C:\...\run-7\script.txt
 *
 *   /maintenance nogui_disablemsg_runscript = open WITHOUT a screen, hide pop-up messages, run the script.
 *
 * Safety: each argument is passed SEPARATELY (a List), not as one command line through cmd.exe.
 * So a space or a quote in a path can never be read as an extra command.
 */
@Component
public class FlexsimLauncher {

    static final String RUN_MODE = "nogui_disablemsg_runscript";

    private final FlexsimSettings settings;
    private final FlexsimScriptBuilder scriptBuilder;

    public FlexsimLauncher(FlexsimSettings settings, FlexsimScriptBuilder scriptBuilder) {
        this.settings = settings;
        this.scriptBuilder = scriptBuilder;
    }

    /** Makes the folder run-<runId> with a model copy and the script. */
    public RunFiles prepare(SimulationTriggerRequestedEvent job, ShiftTimes.Shift shift) throws IOException {
        Path folder = Path.of(settings.workDir()).toAbsolutePath().normalize().resolve("run-" + job.runId());
        Files.createDirectories(folder);

        Path model = folder.resolve("model.fsm");
        Files.copy(Path.of(settings.modelPath()), model, StandardCopyOption.REPLACE_EXISTING);

        Path script = folder.resolve("script.txt");
        Files.writeString(script, scriptBuilder.build(job, shift), StandardCharsets.UTF_8);

        return new RunFiles(folder, model, script, folder.resolve("flexsim-output.log"));
    }

    /** Starts FlexSim. Its printed output goes into the run's log file. */
    public Process start(RunFiles files) throws IOException {
        ProcessBuilder builder = new ProcessBuilder(command(files));
        builder.directory(files.folder().toFile());
        builder.redirectErrorStream(true);                                        // errors into the same log
        builder.redirectOutput(ProcessBuilder.Redirect.appendTo(files.log().toFile()));
        return builder.start();
    }

    List<String> command(RunFiles files) {
        return List.of(
                settings.exePath(),
                files.model().toString(),
                "/maintenance", RUN_MODE,
                "/scriptpath", files.script().toString());
    }
}
