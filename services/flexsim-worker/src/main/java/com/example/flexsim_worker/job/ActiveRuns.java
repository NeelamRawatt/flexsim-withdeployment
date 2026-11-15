package com.example.flexsim_worker.job;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * The list of runs going on RIGHT NOW on this worker (max 4), found by experimentId.
 *
 * Why by experimentId: FlexSim only knows the experimentId (it is in the script), not the runId.
 * That is safe, because simulation-service allows only ONE active run per experiment.
 *
 * ConcurrentHashMap = a map that 4 threads (4 runs) and the web requests from FlexSim can use
 * at the same time without breaking it.
 */
@Component
public class ActiveRuns {

    private final Map<Long, ActiveRun> byExperiment = new ConcurrentHashMap<>();

    ActiveRun start(long runId, long experimentId) {
        ActiveRun run = new ActiveRun(runId, experimentId);
        ActiveRun existing = byExperiment.putIfAbsent(experimentId, run);
        if (existing != null) {
            throw new IllegalStateException("Experiment " + experimentId + " already has run "
                    + existing.runId() + " going on this worker");
        }
        return run;
    }

    /** Used by the localhost API for FlexSim (W4). */
    public Optional<ActiveRun> findByExperiment(long experimentId) {
        return Optional.ofNullable(byExperiment.get(experimentId));
    }

    void remove(ActiveRun run) {
        byExperiment.remove(run.experimentId(), run);
    }

    public int count() {
        return byExperiment.size();
    }
}
