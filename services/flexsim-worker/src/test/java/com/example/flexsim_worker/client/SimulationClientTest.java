package com.example.flexsim_worker.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.example.flexsim_worker.auth.WorkerTokenProvider;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

/**
 * Tests for the calls to simulation-service, WITHOUT a real simulation-service:
 * MockRestServiceServer plays simulation-service; the login is a Mockito fake.
 */
class SimulationClientTest {

    private static final String BASE = "http://simulation.test";
    private static final String RUN_JSON = "{\"runId\":7,\"experimentId\":42,\"status\":\"QUEUED\","
            + "\"stage\":null,\"message\":null,\"requestedBy\":\"operator1\",\"queuedAt\":\"2026-11-15T10:00:00\"}";

    private MockRestServiceServer simulationServer;
    private WorkerTokenProvider tokenProvider;
    private SimulationClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
        simulationServer = MockRestServiceServer.bindTo(builder).build();
        tokenProvider = mock(WorkerTokenProvider.class);
        when(tokenProvider.getToken()).thenReturn("token-1");
        client = new SimulationClient(builder.build(), tokenProvider);
    }

    @Test
    void getRunSendsTheWorkerTokenAndReadsTheRun() {
        simulationServer.expect(once(), requestTo(BASE + "/api/simulation/runs/7"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer token-1"))
                .andRespond(withSuccess(RUN_JSON, MediaType.APPLICATION_JSON));

        Optional<RunView> run = client.getRun(7);

        assertThat(run).isPresent();
        assertThat(run.get().experimentId()).isEqualTo(42L);
        assertThat(run.get().status()).isEqualTo(RunStatus.QUEUED);
        simulationServer.verify();
    }

    @Test
    void runThatNoLongerExistsGivesEmpty() {
        simulationServer.expect(once(), requestTo(BASE + "/api/simulation/runs/7"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThat(client.getRun(7)).isEmpty();
    }

    @Test
    void on401TheWorkerLogsInAgainAndTriesOnce() {
        simulationServer.expect(once(), requestTo(BASE + "/api/simulation/runs/7"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));
        simulationServer.expect(once(), requestTo(BASE + "/api/simulation/runs/7"))
                .andRespond(withSuccess(RUN_JSON, MediaType.APPLICATION_JSON));

        assertThat(client.getRun(7)).isPresent();
        verify(tokenProvider).invalidate();
        simulationServer.verify();
    }

    @Test
    void updateStatusSendsTheRightJson() {
        simulationServer.expect(once(), requestTo(BASE + "/api/simulation/runs/7/status"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer token-1"))
                .andExpect(content().json("{\"status\":\"RUNNING\",\"stage\":\"OPENING_FLEXSIM\",\"message\":\"Opening FlexSim\"}"))
                .andRespond(withSuccess(RUN_JSON.replace("QUEUED", "RUNNING"), MediaType.APPLICATION_JSON));

        Optional<RunView> run = client.updateStatus(7, RunStatus.RUNNING, "OPENING_FLEXSIM", "Opening FlexSim");

        assertThat(run).map(RunView::status).contains(RunStatus.RUNNING);
        verify(tokenProvider, never()).invalidate();
        simulationServer.verify();
    }

    @Test
    void notAllowedChangeIsThrownAs409() {
        simulationServer.expect(once(), requestTo(BASE + "/api/simulation/runs/7/status"))
                .andRespond(withStatus(HttpStatus.CONFLICT));

        assertThatThrownBy(() -> client.updateStatus(7, RunStatus.RUNNING, null, null))
                .isInstanceOf(HttpClientErrorException.Conflict.class);
    }

    @Test
    void tooLongTextsAreCutToWhatSimulationServiceAccepts() {
        assertThat(SimulationClient.cut("x".repeat(600), SimulationClient.MAX_MESSAGE_LENGTH)).hasSize(500);
        assertThat(SimulationClient.cut("short", 50)).isEqualTo("short");
        assertThat(SimulationClient.cut(null, 50)).isNull();
    }
}
