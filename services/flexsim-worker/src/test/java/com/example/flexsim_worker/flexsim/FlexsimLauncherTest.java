package com.example.flexsim_worker.flexsim;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.flexsim_worker.TestJobs;
import com.example.flexsim_worker.event.SimulationTriggerRequestedEvent;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Checks the run folder and the FlexSim command. (Starting the REAL FlexSim is tested on the VM.) */
class FlexsimLauncherTest {

    @TempDir
    Path temp;   // JUnit makes an empty folder for each test and deletes it afterwards

    @Test
    void prepareMakesItsOwnFolderWithModelCopyAndScript() throws Exception {
        Path model = Files.writeString(temp.resolve("Koge Model.fsm"), "model-bytes");
        FlexsimSettings settings = new FlexsimSettings("C:/FlexSim/flexsim.exe", model.toString(),
                temp.resolve("runs").toString(), 360, 60, false);
        FlexsimLauncher launcher = new FlexsimLauncher(settings, new FlexsimScriptBuilder(8090));
        SimulationTriggerRequestedEvent job = TestJobs.nightShift(7, 42);

        RunFiles files = launcher.prepare(job, ShiftTimes.parse(job.startTime(), job.endTime()));

        assertThat(files.folder()).endsWith(Path.of("runs", "run-7"));
        assertThat(files.model()).hasContent("model-bytes");
        assertThat(Files.readString(files.script())).contains("EXPERIMENT_ID = 42;");
        assertThat(files.log().getFileName().toString()).isEqualTo("flexsim-output.log");

        assertThat(launcher.command(files)).containsExactly(
                "C:/FlexSim/flexsim.exe",
                files.model().toString(),
                "/maintenance", "nogui_disablemsg_runscript",
                "/scriptpath", files.script().toString());
    }
}
