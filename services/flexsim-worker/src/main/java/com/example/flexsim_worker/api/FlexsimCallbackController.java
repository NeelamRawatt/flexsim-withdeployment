package com.example.flexsim_worker.api;

import com.example.flexsim_worker.client.FlexsimDataClient;
import com.example.flexsim_worker.job.ActiveRun;
import com.example.flexsim_worker.job.ActiveRuns;
import com.example.flexsim_worker.job.StatusReporter;
import com.example.flexsim_worker.kpi.KpiPublisher;
import java.util.Optional;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;

/**
 * The SMALL API that FlexSim calls during a run, on http://127.0.0.1:8090.
 * Same URLs as the old app, so the FlexSim MODEL needs no change (only the port in the script).
 *
 *   GET  /api/parcels/getParcels/{exp}                 data for FlexSim  (fetched from the owner service)
 *   GET  /api/resourceDetails/getInfeedDetails/{exp}   data for FlexSim
 *   GET  /api/resourceDetails/getZoneDetails/{exp}     data for FlexSim
 *   GET  /api/resourceDetails/getSortPlanDetails/{exp} data for FlexSim
 *   POST /api/flexsim/updateSimulationStage            progress  -> simulation-service (RUNNING, stage)
 *   POST /api/flexsim/kpi/simulationKpi                KPIs      -> Kafka topic simulation.kpi
 *   POST /api/flexsim/completeSimulation               the end   -> the run thread reports COMPLETED
 *   POST /api/flexsim/modelResetMidRun                 only logged (same as the old app)
 *
 * Why no login on these URLs: the worker listens on 127.0.0.1 only (server.address), so only
 * programs on this same VM can call it. And every URL works ONLY for an experiment that has a run
 * going on right now on this worker; for any other experiment the answer is 404.
 *
 * If the data can not be loaded, FlexSim can not simulate: the run is stopped at once (FAILED)
 * instead of waiting hours for the time limit.
 */
@RestController
public class FlexsimCallbackController {

    private static final Logger log = LoggerFactory.getLogger(FlexsimCallbackController.class);

    private final ActiveRuns activeRuns;
    private final FlexsimDataClient dataClient;
    private final StatusReporter reporter;
    private final KpiPublisher kpiPublisher;

    public FlexsimCallbackController(ActiveRuns activeRuns, FlexsimDataClient dataClient,
                                     StatusReporter reporter, KpiPublisher kpiPublisher) {
        this.activeRuns = activeRuns;
        this.dataClient = dataClient;
        this.reporter = reporter;
        this.kpiPublisher = kpiPublisher;
    }

    // ---------------- data for FlexSim ----------------

    @GetMapping(value = "/api/parcels/getParcels/{experimentId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> parcels(@PathVariable long experimentId) {
        return serveData(experimentId, "IMPORTING_PARCEL_DATA", "Importing Parcel Data", "parcel data",
                () -> dataClient.parcels(experimentId));
    }

    @GetMapping(value = "/api/resourceDetails/getInfeedDetails/{experimentId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> infeedDetails(@PathVariable long experimentId) {
        return serveData(experimentId, "GETTING_INFEED_RESOURCE_DETAILS", "Getting Infeed Resource Details",
                "infeed resource details", () -> dataClient.infeedDetails(experimentId));
    }

    @GetMapping(value = "/api/resourceDetails/getZoneDetails/{experimentId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> zoneDetails(@PathVariable long experimentId) {
        return serveData(experimentId, "GETTING_ZONE_RESOURCE_DETAILS", "Getting Zone Resource Details",
                "zone resource details", () -> dataClient.zoneDetails(experimentId));
    }

    @GetMapping(value = "/api/resourceDetails/getSortPlanDetails/{experimentId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> sortPlanDetails(@PathVariable long experimentId) {
        return serveData(experimentId, "GETTING_SORT_PLAN_DETAILS", "Getting Sort Plan Details",
                "sort plan details", () -> dataClient.sortPlanDetails(experimentId));
    }

    private ResponseEntity<String> serveData(long experimentId, String stage, String stageMessage,
                                             String what, Supplier<String> load) {
        Optional<ActiveRun> run = activeRuns.findByExperiment(experimentId);
        if (run.isEmpty()) {
            return noActiveRun(experimentId);
        }
        reporter.progress(run.get().runId(), stage, stageMessage);
        try {
            String body = load.get();
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(body == null ? "[]" : body);
        } catch (RestClientException e) {
            log.error("Run {}: could not load the {} ({})", run.get().runId(), what, e.getMessage());
            run.get().markFailed("Could not load the " + what + " for FlexSim. Please try again later.");
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body("Could not load the " + what);
        }
    }

    // ---------------- progress and the end ----------------

    @PostMapping("/api/flexsim/updateSimulationStage")
    public ResponseEntity<String> updateStage(@RequestBody FlexsimStageUpdate update) {
        Optional<ActiveRun> run = findRun(update);
        if (run.isEmpty()) {
            return noActiveRun(update.experimentId());
        }
        log.info("Run {}: FlexSim stage {} ({})", run.get().runId(), update.experimentRunStage(), update.message());
        reporter.progress(run.get().runId(), update.experimentRunStage(), update.message());
        return ResponseEntity.ok("Simulation stage updated successfully");
    }

    @PostMapping("/api/flexsim/completeSimulation")
    public ResponseEntity<String> complete(@RequestBody FlexsimStageUpdate update) {
        Optional<ActiveRun> run = findRun(update);
        if (run.isEmpty()) {
            return noActiveRun(update.experimentId());
        }
        log.info("Run {}: FlexSim says the simulation is complete", run.get().runId());
        run.get().markFinished(update.message());   // the run thread reports COMPLETED and closes FlexSim
        return ResponseEntity.ok("Simulation marked completed successfully");
    }

    @PostMapping("/api/flexsim/modelResetMidRun")
    public ResponseEntity<String> modelResetMidRun(@RequestBody FlexsimStageUpdate update) {
        log.warn("FlexSim model was reset during the run of experiment {}: {}", update.experimentId(), update.message());
        return ResponseEntity.ok("Model reset mid-run stage updated successfully");
    }

    // ---------------- KPIs ----------------

    @PostMapping(value = "/api/flexsim/kpi/simulationKpi", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> kpi(@RequestBody String body) {
        JsonNode kpi;
        try {
            kpi = kpiPublisher.read(body);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
        long experimentId = KpiPublisher.experimentIdOf(kpi);
        Optional<ActiveRun> run = activeRuns.findByExperiment(experimentId);
        if (run.isEmpty()) {
            return noActiveRun(experimentId);
        }
        try {
            kpiPublisher.publish(run.get(), kpi);
            return ResponseEntity.ok().build();
        } catch (KpiPublisher.KpiPublishException e) {
            log.warn("Run {}: KPI not sent ({})", run.get().runId(), e.getMessage());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body("KPI could not be stored");
        }
    }

    // ---------------- helpers ----------------

    private Optional<ActiveRun> findRun(FlexsimStageUpdate update) {
        return update.experimentId() == null ? Optional.empty() : activeRuns.findByExperiment(update.experimentId());
    }

    private static ResponseEntity<String> noActiveRun(Long experimentId) {
        log.warn("FlexSim called for experiment {}, but no run of it is going on on this worker", experimentId);
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body("No active run for experiment " + experimentId);
    }
}
