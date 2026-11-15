package com.example.flexsim_worker.flexsim;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.flexsim_worker.TestJobs;
import com.example.flexsim_worker.event.SimulationTriggerRequestedEvent;
import org.junit.jupiter.api.Test;

class FlexsimScriptBuilderTest {

    private final FlexsimScriptBuilder builder = new FlexsimScriptBuilder(8090);

    private String scriptFor(SimulationTriggerRequestedEvent job) {
        return builder.build(job, ShiftTimes.parse(job.startTime(), job.endTime()));
    }

    @Test
    void flexsimCallsTheWorkerOnLocalhost8090() {
        String script = scriptFor(TestJobs.nightShift(7, 42));

        assertThat(script).contains(
                "getDataForExperiment(42, \"127.0.0.1\", 8090, \"/api/parcels/getParcels/42\", \"Parcel_Data\")",
                "\"/api/resourceDetails/getInfeedDetails/42\", \"Infeed_Resource_Details\")",
                "\"/api/resourceDetails/getZoneDetails/42\", \"Zone_Resource_Details\")",
                "\"/api/resourceDetails/getSortPlanDetails/42\", \"Sort_Plan_Details\")",
                "API_DETAILS[\"port\"] = 8090;",
                "API_DETAILS[\"simulationCompletePath\"] = \"/api/flexsim/completeSimulation\";",
                "\"/api/flexsim/updateSimulationStage\", \"SIMULATION_STARTED\", \"Simulation Started\")");
        assertThat(script).doesNotContain("8080");
    }

    @Test
    void nightShiftStopsNextDayPlus15Minutes() {
        String script = scriptFor(TestJobs.nightShift(7, 42));

        assertThat(script).contains("DateTime startTime = DateTime(\"10/21/2026 23:00:00\",\"%m/%d/%Y %H:%M:%S\");");
        assertThat(script).contains("DateTime stopTime = DateTime(\"10/22/2026 06:45:00\",\"%m/%d/%Y %H:%M:%S\");");
    }

    @Test
    void settingsFromTheJobAreUsed() {
        String script = scriptFor(TestJobs.nightShift(7, 42));

        assertThat(script).contains(
                "MAXRECIRCULATION = 4;", "EXPERIMENT_ID = 42;", "SIMUSECASE_ID = 2;",
                "FLEXIBLE_RESOURCES = 1;", "PHONE_BOOM_CHUTE = 1;", "DROP_CHUTE_FLEXIBLE = 0;",
                "FLEXIBLE_UNLOAD_RESOURCES = 1;", "SORTER_FULL_PERCENT = 85;",
                "MINIMUM_CAGE_OCCPANCY = 60;", "MAXIMUM_CAGE_OCCPANCY = 80;",
                "fastforward();", "read_sortplan();", "go();");
        assertThat(script).doesNotContain("runspeed(");
    }

    @Test
    void missingSettingsGetTheOldDefaults() {
        String script = scriptFor(TestJobs.withoutSettings(8, 43));

        assertThat(script).contains(
                "MAXRECIRCULATION = 3;", "SIMUSECASE_ID = 0;", "FLEXIBLE_RESOURCES = 1;", "PHONE_BOOM_CHUTE = 1;",
                "DROP_CHUTE_FLEXIBLE = 0;", "FLEXIBLE_UNLOAD_RESOURCES = 0;", "SORTER_FULL_PERCENT = 90;",
                "MINIMUM_CAGE_OCCPANCY = 65;", "MAXIMUM_CAGE_OCCPANCY = 75;");
    }

    @Test
    void sameLimitsAsTheOldApp() {
        assertThat(FlexsimScriptBuilder.sanitizePhoneBoomChute(1, 0)).isZero();      // no flexible resources -> no phone boom
        assertThat(FlexsimScriptBuilder.sanitizeSorterFullPercent(150)).isEqualTo(100);
        assertThat(FlexsimScriptBuilder.sanitizeSorterFullPercent(-5)).isZero();
        assertThat(FlexsimScriptBuilder.sanitizeMinimumCageOccupancy(10)).isEqualTo(50);
        assertThat(FlexsimScriptBuilder.sanitizeMinimumCageOccupancy(99)).isEqualTo(90);
        assertThat(FlexsimScriptBuilder.sanitizeMaximumCageOccupancy(55, 70)).isEqualTo(70);   // never below the minimum
        assertThat(FlexsimScriptBuilder.zeroOrOne(5, 0)).isEqualTo(1);
    }
}
