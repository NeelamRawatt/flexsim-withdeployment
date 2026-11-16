package com.example.kpi_service.kpi;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import java.util.Map;

/**
 * ONE KPI snapshot ("tick") of a run. SAME field names as the old app's SimulationKpiDTO,
 * because both FlexSim (sending) and React (reading) use these names.
 *
 * "chutewise..." maps: key = chute id (for example "CH101").
 * "chutetype..." maps: key = Boom, Drop, Spiral or Reject.
 * chutewiseFilledCageHistory: per chute, per cage position, the cages that became full.
 *   In ONE snapshot it holds only the NEW full cages; the history URL adds them up (see KpiHistoryService).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SimulationKpi(
        Long experimentId,
        Double simulationTime,
        Map<String, Integer> chutewiseThroughput,
        Map<String, Integer> chutewiseBlockage,
        Map<String, Integer> chutewiseDestinated,
        Map<String, Integer> chutewiseDiverted,
        Map<String, List<Integer>> chutewiseCageCount,
        Map<String, List<Integer>> chutewiseCageFillParcelCount,
        Map<String, List<Double>> chutewiseCageFillParcelVolume,
        Map<String, List<List<FilledCage>>> chutewiseFilledCageHistory,
        Map<String, Integer> chutetypeThroughput,
        Map<String, Integer> chutetypeBlockage,
        Map<String, Double> chutewiseTotalBlockageTime,
        Map<String, Integer> chutewiseBlockageState,
        Map<String, ResourceMetrics> resourceMetrics,
        Map<String, InfeedMetrics> infeedMetrics,
        ParcelMetrics parcelMetrics,
        ConveyorDetails conveyorDetails) {

    /** A copy with another filled-cage history (used when the history URL adds them up). */
    public SimulationKpi withFilledCageHistory(Map<String, List<List<FilledCage>>> history) {
        return new SimulationKpi(experimentId, simulationTime, chutewiseThroughput, chutewiseBlockage,
                chutewiseDestinated, chutewiseDiverted, chutewiseCageCount, chutewiseCageFillParcelCount,
                chutewiseCageFillParcelVolume, history, chutetypeThroughput, chutetypeBlockage,
                chutewiseTotalBlockageTime, chutewiseBlockageState, resourceMetrics, infeedMetrics,
                parcelMetrics, conveyorDetails);
    }
}
