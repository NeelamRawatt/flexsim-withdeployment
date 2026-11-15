package com.example.flexsim_worker.job;

import com.example.flexsim_worker.flexsim.FlexsimSettings;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Every night: delete the folders of OLD runs (run-<runId>), so the VM disk never fills up.
 * Each folder holds a model COPY (big), the script and the FlexSim log.
 *
 *   kept    = folders changed in the last 7 days (to read the log of a failed run)
 *   kept    = folders of runs going on now
 *   deleted = everything else that looks like "run-<number>"
 * Other files or folders in the work folder are never touched.
 */
@Component
public class RunFolderCleanup {

    private static final Logger log = LoggerFactory.getLogger(RunFolderCleanup.class);
    private static final Pattern RUN_FOLDER = Pattern.compile("run-(\\d+)");

    private final Path workDir;
    private final Duration keepFor;
    private final ActiveRuns activeRuns;
    private final Clock clock;

    @Autowired
    public RunFolderCleanup(FlexsimSettings settings, ActiveRuns activeRuns, Clock clock,
                            @Value("${app.flexsim.keep-run-folders-days:7}") int keepDays) {
        this(Path.of(settings.workDir()).toAbsolutePath().normalize(), Duration.ofDays(keepDays), activeRuns, clock);
    }

    RunFolderCleanup(Path workDir, Duration keepFor, ActiveRuns activeRuns, Clock clock) {
        this.workDir = workDir;
        this.keepFor = keepFor;
        this.activeRuns = activeRuns;
        this.clock = clock;
    }

    @Scheduled(cron = "${app.flexsim.cleanup-cron:0 30 3 * * *}")
    public void cleanupOldRunFolders() {
        int deleted = deleteOldRunFolders();
        if (deleted > 0) {
            log.info("Cleanup: deleted {} old run folder(s) in {}", deleted, workDir);
        }
    }

    int deleteOldRunFolders() {
        if (!Files.isDirectory(workDir)) {
            return 0;
        }
        Instant cutoff = clock.instant().minus(keepFor);
        int deleted = 0;
        List<Path> folders;
        try (Stream<Path> children = Files.list(workDir)) {
            folders = children.filter(Files::isDirectory).toList();
        } catch (IOException e) {
            log.warn("Cleanup: could not read {} ({})", workDir, e.getMessage());
            return 0;
        }
        for (Path folder : folders) {
            Matcher name = RUN_FOLDER.matcher(folder.getFileName().toString());
            if (!name.matches()) {
                continue;   // not ours
            }
            if (activeRuns.isRunActive(Long.parseLong(name.group(1)))) {
                continue;   // FlexSim is using it right now
            }
            try {
                if (lastChange(folder).isBefore(cutoff)) {
                    deleteFolder(folder);
                    deleted++;
                }
            } catch (IOException e) {
                // For example a file is still open. Try again next night.
                log.warn("Cleanup: could not delete {} ({})", folder, e.getMessage());
            }
        }
        return deleted;
    }

    /** The newest change of anything inside the folder (the log file changes during a run). */
    private static Instant lastChange(Path folder) throws IOException {
        try (Stream<Path> all = Files.walk(folder)) {
            Instant newest = Instant.EPOCH;
            for (Path path : (Iterable<Path>) all::iterator) {
                Instant changed = Files.getLastModifiedTime(path).toInstant();
                if (changed.isAfter(newest)) {
                    newest = changed;
                }
            }
            return newest;
        }
    }

    /** Delete the files first, then the folders (deepest first). */
    private static void deleteFolder(Path folder) throws IOException {
        List<Path> paths;
        try (Stream<Path> all = Files.walk(folder)) {
            paths = all.sorted(Comparator.reverseOrder()).toList();
        }
        for (Path path : paths) {
            Files.deleteIfExists(path);
        }
    }
}
