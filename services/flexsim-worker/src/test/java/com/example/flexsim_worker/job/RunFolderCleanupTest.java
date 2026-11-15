package com.example.flexsim_worker.job;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RunFolderCleanupTest {

    private static final Instant NOW = Instant.parse("2026-11-15T03:30:00Z");

    @TempDir
    Path workDir;

    /** Makes run-<id>\flexsim-output.log, with the given age. */
    private Path runFolder(String name, Duration age) throws Exception {
        Path folder = Files.createDirectories(workDir.resolve(name));
        Path log = Files.writeString(folder.resolve("flexsim-output.log"), "log");
        FileTime time = FileTime.from(NOW.minus(age));
        Files.setLastModifiedTime(log, time);
        Files.setLastModifiedTime(folder, time);
        return folder;
    }

    @Test
    void deletesOnlyOldRunFoldersThatAreNotRunning() throws Exception {
        Path old = runFolder("run-1", Duration.ofDays(10));
        Path recent = runFolder("run-2", Duration.ofDays(2));
        Path oldButRunning = runFolder("run-3", Duration.ofDays(10));
        Path notOurs = runFolder("backup", Duration.ofDays(30));
        ActiveRuns activeRuns = new ActiveRuns();
        activeRuns.start(3, 99);
        RunFolderCleanup cleanup = new RunFolderCleanup(workDir, Duration.ofDays(7), activeRuns,
                Clock.fixed(NOW, ZoneOffset.UTC));

        int deleted = cleanup.deleteOldRunFolders();

        assertThat(deleted).isEqualTo(1);
        assertThat(old).doesNotExist();
        assertThat(recent).exists();
        assertThat(oldButRunning).exists();
        assertThat(notOurs).exists();
    }

    @Test
    void missingWorkFolderIsNoProblem() {
        RunFolderCleanup cleanup = new RunFolderCleanup(workDir.resolve("does-not-exist"), Duration.ofDays(7),
                new ActiveRuns(), Clock.fixed(NOW, ZoneOffset.UTC));

        assertThat(cleanup.deleteOldRunFolders()).isZero();
    }
}
