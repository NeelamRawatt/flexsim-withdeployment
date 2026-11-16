package com.example.kpi_service.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.kpi_service.kpi.FilledCage;
import com.example.kpi_service.kpi.SimulationKpi;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class KpiHistoryServiceTest {

    private static FilledCage cage(double fullAt) {
        return new FilledCage(40, 1.2, fullAt, null, null, null, "Operator1", 30.0);
    }

    private static SimulationKpi tick(double time, Map<String, List<List<FilledCage>>> newCages,
                                      Map<String, Integer> blockageState) {
        return new SimulationKpi(42L, time, Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(),
                newCages, Map.of(), Map.of(), Map.of(), blockageState, Map.of(), Map.of(), null, null);
    }

    @Test
    void fullCagesAreAddedUpOverTime() {
        List<SimulationKpi> ticks = List.of(
                tick(60, Map.of("CH1", List.of(List.of(cage(50)))), Map.of()),
                tick(120, Map.of(), Map.of()),
                tick(180, Map.of("CH1", List.of(List.of(), List.of(cage(170))), "CH2", List.of(List.of(cage(175)))), Map.of()));

        List<SimulationKpi> history = KpiHistoryService.addUpFilledCages(ticks);

        assertThat(history.get(0).chutewiseFilledCageHistory().get("CH1").get(0)).hasSize(1);
        assertThat(history.get(1).chutewiseFilledCageHistory().get("CH1").get(0)).hasSize(1);   // nothing new: still 1
        Map<String, List<List<FilledCage>>> last = history.get(2).chutewiseFilledCageHistory();
        assertThat(last.get("CH1")).hasSize(2);
        assertThat(last.get("CH1").get(0)).hasSize(1);
        assertThat(last.get("CH1").get(1)).hasSize(1);
        assertThat(last.get("CH2").get(0)).hasSize(1);
        // each entry has its OWN copy: the first entry did not change later
        assertThat(history.get(0).chutewiseFilledCageHistory()).containsOnlyKeys("CH1");
    }

    @Test
    void blockageIntervalsFollowTheOldRules() {
        List<SimulationKpi> ticks = List.of(
                tick(10, Map.of(), Map.of("CH1", 0, "CH2", 1)),
                tick(20, Map.of(), Map.of("CH1", 1, "CH2", 1)),
                tick(30, Map.of(), Map.of("CH1", 1, "CH2", 0)),
                tick(40, Map.of(), Map.of("CH1", 0, "CH2", 1)));

        List<BlockageIntervalService.ChuteBlockageInterval> intervals = BlockageIntervalService.compute(42L, ticks);

        assertThat(intervals).containsExactly(
                new BlockageIntervalService.ChuteBlockageInterval(42L, "CH1", 20.0, 30.0, 10.0),
                new BlockageIntervalService.ChuteBlockageInterval(42L, "CH2", 10.0, 20.0, 10.0),
                new BlockageIntervalService.ChuteBlockageInterval(42L, "CH2", 40.0, 40.0, 0.0));   // still open at the end
    }
}
