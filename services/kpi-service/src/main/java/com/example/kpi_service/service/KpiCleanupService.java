package com.example.kpi_service.service;

import com.example.kpi_service.repository.KpiSnapshotRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Deletes all KPIs of a deleted experiment (called by ExperimentDeletedListener). */
@Service
public class KpiCleanupService {

    private final KpiSnapshotRepository repository;

    public KpiCleanupService(KpiSnapshotRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public int deleteAllOfExperiment(Long experimentId) {
        return repository.deleteByExperimentId(experimentId);
    }
}
