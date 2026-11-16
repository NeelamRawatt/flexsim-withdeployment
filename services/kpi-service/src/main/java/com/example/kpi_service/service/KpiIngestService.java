package com.example.kpi_service.service;

import com.example.kpi_service.entity.KpiSnapshot;
import com.example.kpi_service.event.SimulationKpiEvent;
import com.example.kpi_service.kpi.KpiNormalizer;
import com.example.kpi_service.kpi.SimulationKpi;
import com.example.kpi_service.repository.KpiSnapshotRepository;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

/**
 * Saves ONE KPI snapshot from Kafka.
 *
 * IDEMPOTENT (safe to repeat): Kafka delivers "at least once", so the same message can come twice
 * (for example after a restart). The eventId is UNIQUE in the table:
 *   1. quick check "already saved?" -> skip
 *   2. if two copies of kpi-service save it at the very same moment, the database refuses the
 *      second one (unique key) -> also skip. Never a duplicate snapshot.
 */
@Service
public class KpiIngestService {

    private static final Logger log = LoggerFactory.getLogger(KpiIngestService.class);

    private final KpiSnapshotRepository repository;
    private final JsonMapper json = JsonMapper.builder().build();

    public KpiIngestService(KpiSnapshotRepository repository) {
        this.repository = repository;
    }

    /** @return true = saved now, false = it was already saved before */
    public boolean save(SimulationKpiEvent event) {
        if (repository.existsByEventId(event.eventId())) {
            return false;
        }
        SimulationKpi tick = KpiNormalizer.normalize(event.kpi());
        KpiSnapshot snapshot = new KpiSnapshot(event.eventId(), event.runId(), event.experimentId(),
                tick.simulationTime(), LocalDateTime.now(ZoneOffset.UTC), json.writeValueAsString(tick));
        try {
            repository.saveAndFlush(snapshot);
            return true;
        } catch (DataIntegrityViolationException e) {
            log.debug("KPI snapshot {} was saved by another copy at the same moment", event.eventId());
            return false;
        }
    }
}
