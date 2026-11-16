package com.example.kpi_service.kpi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class KpiNormalizerTest {

    private static SimulationKpi raw(Map<String, Integer> throughput, Map<String, Integer> blockageState,
                                     Map<String, Integer> blockage, Map<String, Integer> typeThroughput,
                                     Map<String, Integer> typeBlockage) {
        return new SimulationKpi(42L, 120.0, throughput, blockage, null, null,
                Map.of("CH2", List.of(1, 0)), null, null, null,
                typeThroughput, typeBlockage, Map.of("CH1", 0.0, "CH2", 35.5), blockageState,
                Map.of("Operator_ZONE-2_1_7", new ResourceMetrics(null, 9, 15, null), "Forklift", new ResourceMetrics(null, null, null, 4)),
                Map.of("IF-02", new InfeedMetrics(null)),
                new ParcelMetrics(null, 100, 90, 2, 1, 0, null),
                new ConveyorDetails(261, null));
    }

    @Test
    void everyChuteGetsTheBasicNumbersAndZeroBlockagesAreLeftOut() {
        SimulationKpi tick = KpiNormalizer.normalize(raw(
                Map.of("CH1", 10), Map.of("CH2", 1), Map.of("CH1", 0, "CH2", 3), Map.of(), Map.of()));

        assertThat(tick.chutewiseThroughput()).containsOnly(entry("CH1", 10), entry("CH2", 0));
        assertThat(tick.chutewiseDestinated()).containsOnly(entry("CH1", 0), entry("CH2", 0));
        assertThat(tick.chutewiseBlockageState()).containsOnly(entry("CH1", 0), entry("CH2", 1));
        assertThat(tick.chutewiseBlockage()).containsOnly(entry("CH2", 3));            // CH1 had 0
        assertThat(tick.chutewiseTotalBlockageTime()).containsOnly(entry("CH2", 35.5));
        assertThat(tick.chutewiseCageCount()).containsOnlyKeys("CH2");
        assertThat(tick.chutewiseFilledCageHistory()).isEmpty();
    }

    @Test
    void chuteTypesAreTitleCasedAndUnknownTypesSkipped() {
        SimulationKpi tick = KpiNormalizer.normalize(raw(Map.of(), Map.of(), Map.of(),
                Map.of("BOOM", 5, "spiral", 7, "LASER", 99), Map.of("BOOM", 0, "SPIRAL", 2)));

        assertThat(tick.chutetypeThroughput()).containsOnly(entry("Boom", 5), entry("Spiral", 7));
        assertThat(tick.chutetypeBlockage()).containsOnly(entry("Spiral", 2));
    }

    @Test
    void resourcesConveyorParcelsAndInfeedsAreCleanedUp() {
        SimulationKpi tick = KpiNormalizer.normalize(raw(Map.of(), Map.of(), Map.of(), Map.of(), Map.of()));

        assertThat(tick.resourceMetrics().get("Operator_ZONE-2_1_7")).isEqualTo(new ResourceMetrics(2, 1, 15, 0));
        assertThat(tick.resourceMetrics().get("Forklift")).isEqualTo(new ResourceMetrics(null, null, 0, 4));
        assertThat(tick.conveyorDetails()).isEqualTo(new ConveyorDetails(261, 0.5));
        assertThat(tick.parcelMetrics().experimentId()).isEqualTo(42L);
        assertThat(tick.parcelMetrics().simulationTime()).isEqualTo(120.0);
        assertThat(tick.infeedMetrics().get("IF-02")).isEqualTo(new InfeedMetrics(0));
    }

    @Test
    void anEmptySnapshotGivesEmptyMapsNotNulls() {
        SimulationKpi tick = KpiNormalizer.normalize(new SimulationKpi(42L, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null));

        assertThat(tick.simulationTime()).isEqualTo(0.0);
        assertThat(tick.chutewiseThroughput()).isEmpty();
        assertThat(tick.resourceMetrics()).isEmpty();
        assertThat(tick.parcelMetrics()).isNull();
        assertThat(tick.conveyorDetails()).isNull();
    }
}
