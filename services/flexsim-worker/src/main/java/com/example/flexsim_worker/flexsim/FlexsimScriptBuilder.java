package com.example.flexsim_worker.flexsim;

import com.example.flexsim_worker.event.SimulationTriggerRequestedEvent;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Writes script.txt: the list of commands FlexSim runs when it opens the model.
 * This is the SAME script as in the old app (FlexsimScriptService), with 3 changes:
 *
 *   1. FlexSim calls back the WORKER on 127.0.0.1:8090 (the old app was on 8080).
 *      The URL paths stay the same, so the FlexSim MODEL does not need any change.
 *      (The worker answers these URLs in W4.)
 *   2. Always "fastforward();" = no screen (there is nobody watching a screen on the Azure VM).
 *   3. Night shift: an end time before the start time means the next day (see ShiftTimes).
 *
 * What the script does, in order:
 *   - get the data: parcels, infeed resources, zone resources, sort plan
 *   - set the start and stop time (stop = shift end + 15 minutes, as before)
 *   - tell us "STARTING_SIMULATION", set the model settings, then go()
 *   - tell us "SIMULATION_STARTED"
 *   During the run FlexSim sends KPIs; at the end it calls completeSimulation.
 */
@Component
public class FlexsimScriptBuilder {

    // ----- the same limits and defaults as the old app -----
    static final int DEFAULT_FLEXIBLE_RESOURCES = 1;
    static final int MIN_CAGE_OCCUPANCY_FLOOR = 50;
    static final int MAX_CAGE_OCCUPANCY_CEILING = 90;
    static final int DEFAULT_MIN_CAGE_OCCUPANCY = 65;
    static final int DEFAULT_MAX_CAGE_OCCUPANCY = 75;
    static final int DEFAULT_SORTER_FULL_PERCENT = 90;
    static final int DEFAULT_MAX_RECIRCULATION = 3;
    static final int DEFAULT_USE_CASE_ID = 0;
    /** FlexSim runs 15 minutes longer than the shift (same as the old app). */
    static final Duration EXTRA_AFTER_SHIFT = Duration.ofMinutes(15);

    static final String HOST = "127.0.0.1";
    private static final DateTimeFormatter FLEXSIM_TIME = DateTimeFormatter.ofPattern("M/d/yyyy HH:mm:ss");

    private final int callbackPort;

    /** callbackPort = the worker's own port (server.port = 8090). */
    public FlexsimScriptBuilder(@Value("${server.port}") int callbackPort) {
        this.callbackPort = callbackPort;
    }

    public String build(SimulationTriggerRequestedEvent job, ShiftTimes.Shift shift) {
        long experimentId = job.experimentId();

        String parcelFunction = getData(experimentId, "/api/parcels/getParcels/" + experimentId, "Parcel_Data");
        String infeedResourceFunction = getData(experimentId,
                "/api/resourceDetails/getInfeedDetails/" + experimentId, "Infeed_Resource_Details");
        String zoneResourceFunction = getData(experimentId,
                "/api/resourceDetails/getZoneDetails/" + experimentId, "Zone_Resource_Details");
        String sortPlanFunction = getData(experimentId,
                "/api/resourceDetails/getSortPlanDetails/" + experimentId, "Sort_Plan_Details");

        LocalDateTime stop = shift.end().plus(EXTRA_AFTER_SHIFT);
        String setFlexsimStartTime = String.format(
                """
                DateTime startTime = DateTime("%s","%%m/%%d/%%Y %%H:%%M:%%S");
                function_s(getmodelunit(START_TIME_NODE), "setDateTime", startTime);
                """, FLEXSIM_TIME.format(shift.start()));
        String setFlexsimStopTime = String.format(
                """
                DateTime stopTime = DateTime("%s","%%m/%%d/%%Y %%H:%%M:%%S");
                int s = stopTime.totalSeconds - startTime.totalSeconds;
                stoptime(s);
                """, FLEXSIM_TIME.format(stop));

        String updateStagePath = "/api/flexsim/updateSimulationStage";
        String simulationStartingFunction = updateStatus(experimentId, updateStagePath,
                "STARTING_SIMULATION", "Starting Simulation");
        String simulationStartedFunction = updateStatus(experimentId, updateStagePath,
                "SIMULATION_STARTED", "Simulation Started");

        int flexibleResources = sanitizeFlexibleResources(job.flexibleResources());
        int minimumCageOccupancy = sanitizeMinimumCageOccupancy(job.minimumCageOccupancy());

        // The old app called the parcel function TWICE. We keep it exactly like that:
        // the FlexSim model was tested this way.
        return String.format(
                """
                %s;
                %s;
                %s;
                %s;
                %s;
                %s
                %s
                %s;
                resetmodel();
                resetmodel();
                MAXRECIRCULATION = %d;
                EXPERIMENT_ID = %d;
                SIMUSECASE_ID = %d;
                API_DETAILS["host"] = "%s";
                API_DETAILS["port"] = %d;
                API_DETAILS["flexsimKpiPath"] = "%s";
                API_DETAILS["simulationCompletePath"] = "%s";
                SIMULATION_START_TIME = startTime;
                SIMULATION_END_TIME = stopTime;
                %s
                FLEXIBLE_RESOURCES = %d;
                PHONE_BOOM_CHUTE = %d;
                DROP_CHUTE_FLEXIBLE = %d;
                FLEXIBLE_UNLOAD_RESOURCES = %d;
                SORTER_FULL_PERCENT = %d;
                MINIMUM_CAGE_OCCPANCY = %d;
                MAXIMUM_CAGE_OCCPANCY = %d;
                read_sortplan();
                go();
                %s;
                """,
                parcelFunction,
                parcelFunction,
                infeedResourceFunction,
                zoneResourceFunction,
                sortPlanFunction,
                setFlexsimStartTime,
                setFlexsimStopTime,
                simulationStartingFunction,
                job.maxRecirculationCount() == null ? DEFAULT_MAX_RECIRCULATION : job.maxRecirculationCount(),
                experimentId,
                job.useCaseId() == null ? DEFAULT_USE_CASE_ID : job.useCaseId(),
                HOST,
                callbackPort,
                "/api/flexsim/kpi/simulationKpi",
                "/api/flexsim/completeSimulation",
                "fastforward();",
                flexibleResources,
                sanitizePhoneBoomChute(job.phoneBoomChute(), flexibleResources),
                zeroOrOne(job.dropChuteFlexible(), 0),
                zeroOrOne(job.flexibleUnloadResources(), 0),
                sanitizeSorterFullPercent(job.sorterFullPercent()),
                minimumCageOccupancy,
                sanitizeMaximumCageOccupancy(job.maximumCageOccupancy(), minimumCageOccupancy),
                simulationStartedFunction);
    }

    private String getData(long experimentId, String path, String tableName) {
        return String.format("getDataForExperiment(%d, \"%s\", %d, \"%s\", \"%s\")",
                experimentId, HOST, callbackPort, path, tableName);
    }

    private String updateStatus(long experimentId, String path, String stage, String message) {
        return String.format("updateSimulationStatus(%d, \"%s\", %d, \"%s\", \"%s\", \"%s\")",
                experimentId, HOST, callbackPort, path, stage, message);
    }

    // ----- the same rules as the old app -----

    static int sanitizeFlexibleResources(Integer value) {
        return zeroOrOne(value, DEFAULT_FLEXIBLE_RESOURCES);
    }

    static int sanitizePhoneBoomChute(Integer value, int flexibleResources) {
        if (flexibleResources == 0) {
            return 0;
        }
        return zeroOrOne(value, 1);
    }

    /** null -> the default; 0 -> 0; anything else -> 1 */
    static int zeroOrOne(Integer value, int defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        return value == 0 ? 0 : 1;
    }

    static int sanitizeSorterFullPercent(Integer value) {
        if (value == null) {
            return DEFAULT_SORTER_FULL_PERCENT;
        }
        return Math.min(100, Math.max(0, value));
    }

    static int sanitizeMinimumCageOccupancy(Integer value) {
        if (value == null) {
            return DEFAULT_MIN_CAGE_OCCUPANCY;
        }
        return Math.min(MAX_CAGE_OCCUPANCY_CEILING, Math.max(MIN_CAGE_OCCUPANCY_FLOOR, value));
    }

    static int sanitizeMaximumCageOccupancy(Integer value, int minimumCageOccupancy) {
        int maximum = value == null
                ? DEFAULT_MAX_CAGE_OCCUPANCY
                : Math.min(MAX_CAGE_OCCUPANCY_CEILING, Math.max(MIN_CAGE_OCCUPANCY_FLOOR, value));
        return Math.max(maximum, minimumCageOccupancy);
    }
}
