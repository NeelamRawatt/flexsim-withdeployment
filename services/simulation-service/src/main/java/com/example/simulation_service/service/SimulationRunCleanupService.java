package com.example.simulation_service.service;

import com.example.simulation_service.repository.SimulationRunRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Deletes the runs of an experiment that was deleted in experiment-service.
 *
 * What if a run of that experiment is still RUNNING in FlexSim? Its row is deleted anyway.
 * The worker's next status update then gets 404 "Run not found", and the worker treats 404 as
 * "stop this job" (built in the worker step). So nothing is left half-done.
 */
@Service
public class SimulationRunCleanupService {

    private final SimulationRunRepository repository;

    public SimulationRunCleanupService(SimulationRunRepository repository) {
        this.repository = repository;
    }

    /** Returns how many runs were deleted. 0 is normal (no runs, or a duplicate message). */
    @Transactional
    public int deleteAllRunsOfExperiment(Long experimentId) {
        return repository.deleteByExperimentId(experimentId);
    }
}
