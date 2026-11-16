package com.example.kpi_service.service;

import com.example.kpi_service.kpi.SimulationKpi;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.stereotype.Service;

/**
 * WHEN was each chute blocked? (GET /api/metrics/blockage-intervals/{exp}) Same rules as the old app:
 *
 *   go through the snapshots in time order, per chute:
 *     blocked now, not before  -> an interval STARTS here
 *     still blocked            -> remember this time as "last blocked"
 *     not blocked any more     -> the interval ENDS at "last blocked"
 *   still blocked at the last snapshot -> the interval ends at "last blocked"
 * Sorted by chute, then by start time.
 */
@Service
public class BlockageIntervalService {

    private final KpiHistoryService historyService;

    public BlockageIntervalService(KpiHistoryService historyService) {
        this.historyService = historyService;
    }

    /** One blocked period of one chute. Same JSON as the old app. */
    public record ChuteBlockageInterval(Long experimentId, String chuteId, Double fromSimSec, Double toSimSec,
                                        Double durationSec) {
    }

    public List<ChuteBlockageInterval> intervals(Long experimentId) {
        return compute(experimentId, historyService.ticks(experimentId));
    }

    static List<ChuteBlockageInterval> compute(Long experimentId, List<SimulationKpi> ticks) {
        // chute -> [start of the open interval, last blocked time]
        Map<String, Double[]> open = new TreeMap<>();
        List<ChuteBlockageInterval> intervals = new ArrayList<>();
        for (SimulationKpi tick : ticks) {
            Map<String, Integer> states = tick.chutewiseBlockageState() == null ? Map.of() : tick.chutewiseBlockageState();
            states.forEach((chute, state) -> {
                boolean blocked = state != null && state == 1;
                Double[] interval = open.get(chute);
                if (blocked) {
                    if (interval == null) {
                        open.put(chute, new Double[] {tick.simulationTime(), tick.simulationTime()});
                    } else {
                        interval[1] = tick.simulationTime();
                    }
                } else if (interval != null) {
                    intervals.add(interval(experimentId, chute, interval));
                    open.remove(chute);
                }
            });
        }
        open.forEach((chute, interval) -> intervals.add(interval(experimentId, chute, interval)));
        intervals.sort(Comparator.comparing(ChuteBlockageInterval::chuteId)
                .thenComparingDouble(ChuteBlockageInterval::fromSimSec));
        return intervals;
    }

    private static ChuteBlockageInterval interval(Long experimentId, String chute, Double[] interval) {
        return new ChuteBlockageInterval(experimentId, chute, interval[0], interval[1], interval[1] - interval[0]);
    }
}
