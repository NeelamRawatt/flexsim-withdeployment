package com.example.flexsim_worker.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.flexsim_worker.client.FlexsimDataClient;
import com.example.flexsim_worker.job.ActiveRun;
import com.example.flexsim_worker.job.ActiveRuns;
import com.example.flexsim_worker.job.StatusReporter;
import com.example.flexsim_worker.kpi.KpiPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.client.ResourceAccessException;

/**
 * Plays FlexSim: calls the worker's localhost API with MockMvc (a fake web request, no real server).
 * The services behind it are fakes (Mockito).
 */
class FlexsimCallbackControllerTest {

    private ActiveRuns activeRuns;
    private FlexsimDataClient dataClient;
    private StatusReporter reporter;
    private KpiPublisher kpiPublisher;
    private MockMvc mvc;
    private ActiveRun run;

    @BeforeEach
    void setUp() {
        activeRuns = new ActiveRuns();
        dataClient = mock(FlexsimDataClient.class);
        reporter = mock(StatusReporter.class);
        kpiPublisher = mock(KpiPublisher.class);
        mvc = MockMvcBuilders.standaloneSetup(
                new FlexsimCallbackController(activeRuns, dataClient, reporter, kpiPublisher)).build();
        run = activeRuns.start(7, 42);
    }

    @Test
    void parcelsArePassedOnExactlyAndTheStageIsReported() throws Exception {
        when(dataClient.parcels(42)).thenReturn("[{\"parcelId\":\"P1\"}]");

        mvc.perform(get("/api/parcels/getParcels/42"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().string("[{\"parcelId\":\"P1\"}]"));

        verify(reporter).progress(7, "IMPORTING_PARCEL_DATA", "Importing Parcel Data");
    }

    @Test
    void resourceListsComeFromExperimentService() throws Exception {
        when(dataClient.infeedDetails(42)).thenReturn("[1]");
        when(dataClient.zoneDetails(42)).thenReturn("[2]");
        when(dataClient.sortPlanDetails(42)).thenReturn("[3]");

        mvc.perform(get("/api/resourceDetails/getInfeedDetails/42")).andExpect(content().string("[1]"));
        mvc.perform(get("/api/resourceDetails/getZoneDetails/42")).andExpect(content().string("[2]"));
        mvc.perform(get("/api/resourceDetails/getSortPlanDetails/42")).andExpect(content().string("[3]"));

        verify(reporter).progress(7, "GETTING_SORT_PLAN_DETAILS", "Getting Sort Plan Details");
    }

    @Test
    void dataThatCanNotBeLoadedStopsTheRun() throws Exception {
        when(dataClient.parcels(42)).thenThrow(new ResourceAccessException("experiment-service down"));

        mvc.perform(get("/api/parcels/getParcels/42")).andExpect(status().isBadGateway());

        ActiveRun.Outcome outcome = activeRuns.findByExperiment(42).orElseThrow().result().orElseThrow();
        assertThat(outcome.completed()).isFalse();
        assertThat(outcome.message()).contains("parcel data");
    }

    @Test
    void experimentWithoutARunHereGets404() throws Exception {
        mvc.perform(get("/api/parcels/getParcels/999")).andExpect(status().isNotFound());

        verifyNoInteractions(dataClient, reporter);
    }

    @Test
    void stageUpdateGoesToSimulationService() throws Exception {
        mvc.perform(post("/api/flexsim/updateSimulationStage").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"experimentId\":42,\"experimentRunStage\":\"SIMULATION_STARTED\",\"message\":\"Simulation Started\"}"))
                .andExpect(status().isOk());

        verify(reporter).progress(7, "SIMULATION_STARTED", "Simulation Started");
    }

    @Test
    void completeMarksTheRunFinished() throws Exception {
        mvc.perform(post("/api/flexsim/completeSimulation").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"experimentId\":42,\"experimentRunStage\":\"FINISHED\",\"message\":\"Simulation completed\"}"))
                .andExpect(status().isOk());

        assertThat(run.result().orElseThrow()).isEqualTo(new ActiveRun.Outcome(true, "Simulation completed"));
    }

    @Test
    void kpiIsPublished() throws Exception {
        KpiPublisher real = new KpiPublisher(null, null, 1);
        String body = "{\"experimentId\":42,\"simulationTime\":60.0}";
        when(kpiPublisher.read(body)).thenReturn(real.read(body));

        mvc.perform(post("/api/flexsim/kpi/simulationKpi").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());

        verify(kpiPublisher).publish(any(ActiveRun.class), any());
    }

    @Test
    void kpiThatKafkaDoesNotTakeGets503() throws Exception {
        KpiPublisher real = new KpiPublisher(null, null, 1);
        String body = "{\"experimentId\":42}";
        when(kpiPublisher.read(body)).thenReturn(real.read(body));
        doThrow(new KpiPublisher.KpiPublishException("down", null)).when(kpiPublisher).publish(any(), any());

        mvc.perform(post("/api/flexsim/kpi/simulationKpi").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void brokenKpiGets400() throws Exception {
        when(kpiPublisher.read("oops")).thenThrow(new IllegalArgumentException("KPI message is not valid JSON"));

        mvc.perform(post("/api/flexsim/kpi/simulationKpi").contentType(MediaType.APPLICATION_JSON).content("oops"))
                .andExpect(status().isBadRequest());
    }
}
