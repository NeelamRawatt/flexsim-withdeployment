package com.example.kpi_service.service;

import com.example.kpi_service.entity.KpiSnapshot;
import com.example.kpi_service.kpi.FilledCage;
import com.example.kpi_service.kpi.SimulationKpi;
import com.example.kpi_service.repository.KpiSnapshotRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * The whole KPI history of an experiment, for React's charts (GET /api/metrics/{exp}).
 * Same result as the old app's getMetricsHistory:
 *
 *   1. one entry per simulation time, in time order
 *      (if the same time was sent twice, the LATEST one counts: nothing is counted double)
 *   2. full cages: each snapshot only holds the NEW full cages, so we ADD THEM UP:
 *      every entry shows all full cages from the start of the run until that moment.
 */
@Service
@Transactional(readOnly = true)
public class KpiHistoryService {

    private final KpiSnapshotRepository repository;
    private final JsonMapper json = JsonMapper.builder().build();

    public KpiHistoryService(KpiSnapshotRepository repository) {
        this.repository = repository;
    }

    /** The snapshots as saved (one per simulation time), without adding up the cages. */
    public List<SimulationKpi> ticks(Long experimentId) {
        Map<Double, SimulationKpi> byTime = new TreeMap<>();
        for (KpiSnapshot snapshot : repository.findByExperimentIdOrderBySimulationTimeAscIdAsc(experimentId)) {
            byTime.put(snapshot.getSimulationTime(), json.readValue(snapshot.getPayload(), SimulationKpi.class));
        }
        return new ArrayList<>(byTime.values());
    }

    public List<SimulationKpi> history(Long experimentId) {
        return addUpFilledCages(ticks(experimentId));
    }

    /** Per chute and cage position: all full cages so far. Each entry gets its own copy. */
    static List<SimulationKpi> addUpFilledCages(List<SimulationKpi> ticks) {
        Map<String, List<List<FilledCage>>> running = new LinkedHashMap<>();
        List<SimulationKpi> result = new ArrayList<>(ticks.size());
        for (SimulationKpi tick : ticks) {
            if (tick.chutewiseFilledCageHistory() != null) {
                tick.chutewiseFilledCageHistory().forEach((chute, positions) -> {
                    if (positions == null || positions.isEmpty()) {
                        return;
                    }
                    List<List<FilledCage>> total = running.computeIfAbsent(chute, key -> new ArrayList<>());
                    for (int position = 0; position < positions.size(); position++) {
                        List<FilledCage> newCages = positions.get(position);
                        if (newCages == null || newCages.isEmpty()) {
                            continue;
                        }
                        while (total.size() <= position) {
                            total.add(new ArrayList<>());
                        }
                        total.get(position).addAll(newCages);
                    }
                });
            }
            result.add(tick.withFilledCageHistory(copy(running)));
        }
        return result;
    }

    private static Map<String, List<List<FilledCage>>> copy(Map<String, List<List<FilledCage>>> source) {
        Map<String, List<List<FilledCage>>> copy = new LinkedHashMap<>();
        source.forEach((chute, positions) -> {
            List<List<FilledCage>> positionsCopy = new ArrayList<>(positions.size());
            positions.forEach(cages -> positionsCopy.add(new ArrayList<>(cages)));
            copy.put(chute, positionsCopy);
        });
        return copy;
    }
}
