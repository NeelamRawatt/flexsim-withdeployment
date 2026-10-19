package com.example.experiment_service.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.example.experiment_service.service.ExperimentRules;

/** Tests the business rules (ranges and defaults) one by one. */
class ExperimentRulesTest {

    @Test
    void flexibleResourcesIsOnlyZeroOrOne() {
        assertThat(ExperimentRules.flexibleResources(null)).isEqualTo(1);
        assertThat(ExperimentRules.flexibleResources(0)).isEqualTo(0);
        assertThat(ExperimentRules.flexibleResources(1)).isEqualTo(1);
        assertThat(ExperimentRules.flexibleResources(5)).isEqualTo(1);
        assertThat(ExperimentRules.flexibleResources(-1)).isEqualTo(1);
    }

    @Test
    void phoneBoomChuteIsZeroWhenFlexibleResourcesIsZero() {
        assertThat(ExperimentRules.phoneBoomChute(1, 0)).isEqualTo(0);
        assertThat(ExperimentRules.phoneBoomChute(null, 0)).isEqualTo(0);
    }

    @Test
    void phoneBoomChuteDefaultsToOne() {
        assertThat(ExperimentRules.phoneBoomChute(null, 1)).isEqualTo(1);
        assertThat(ExperimentRules.phoneBoomChute(null, null)).isEqualTo(1);
        assertThat(ExperimentRules.phoneBoomChute(0, 1)).isEqualTo(0);
        assertThat(ExperimentRules.phoneBoomChute(7, 1)).isEqualTo(1);
    }

    @Test
    void dropChuteFlexibleAndUnloadResourcesDefaultToZero() {
        assertThat(ExperimentRules.dropChuteFlexible(null)).isEqualTo(0);
        assertThat(ExperimentRules.dropChuteFlexible(0)).isEqualTo(0);
        assertThat(ExperimentRules.dropChuteFlexible(3)).isEqualTo(1);
        assertThat(ExperimentRules.flexibleUnloadResources(null)).isEqualTo(0);
        assertThat(ExperimentRules.flexibleUnloadResources(0)).isEqualTo(0);
        assertThat(ExperimentRules.flexibleUnloadResources(2)).isEqualTo(1);
    }

    @Test
    void sorterFullPercentIsBetweenZeroAndHundred() {
        assertThat(ExperimentRules.sorterFullPercent(null)).isEqualTo(90);
        assertThat(ExperimentRules.sorterFullPercent(150)).isEqualTo(100);
        assertThat(ExperimentRules.sorterFullPercent(-5)).isEqualTo(0);
        assertThat(ExperimentRules.sorterFullPercent(40)).isEqualTo(40);
    }

    @Test
    void minimumCageOccupancyIsBetween50And90() {
        assertThat(ExperimentRules.minimumCageOccupancy(null)).isEqualTo(65);
        assertThat(ExperimentRules.minimumCageOccupancy(10)).isEqualTo(50);
        assertThat(ExperimentRules.minimumCageOccupancy(95)).isEqualTo(90);
        assertThat(ExperimentRules.minimumCageOccupancy(70)).isEqualTo(70);
    }

    @Test
    void maximumCageOccupancyIsBetween50And90AndNeverBelowMinimum() {
        assertThat(ExperimentRules.maximumCageOccupancy(null, 65)).isEqualTo(75);
        assertThat(ExperimentRules.maximumCageOccupancy(95, 65)).isEqualTo(90);
        assertThat(ExperimentRules.maximumCageOccupancy(10, 65)).isEqualTo(65);
        assertThat(ExperimentRules.maximumCageOccupancy(null, 80)).isEqualTo(80);
        assertThat(ExperimentRules.maximumCageOccupancy(60, 55)).isEqualTo(60);
    }
}