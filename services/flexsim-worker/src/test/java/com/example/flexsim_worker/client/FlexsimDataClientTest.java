package com.example.flexsim_worker.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.example.flexsim_worker.auth.WorkerTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class FlexsimDataClientTest {

    private MockRestServiceServer experimentService;
    private MockRestServiceServer parcelService;
    private WorkerTokenProvider tokenProvider;
    private FlexsimDataClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder experimentBuilder = RestClient.builder().baseUrl("http://experiment.test");
        RestClient.Builder parcelBuilder = RestClient.builder().baseUrl("http://parcel.test");
        experimentService = MockRestServiceServer.bindTo(experimentBuilder).build();
        parcelService = MockRestServiceServer.bindTo(parcelBuilder).build();
        tokenProvider = mock(WorkerTokenProvider.class);
        when(tokenProvider.getToken()).thenReturn("worker-token");
        client = new FlexsimDataClient(experimentBuilder.build(), parcelBuilder.build(), tokenProvider);
    }

    @Test
    void resourceListsComeFromExperimentServiceWithTheWorkerToken() {
        experimentService.expect(once(), requestTo("http://experiment.test/api/resourceDetails/getZoneDetails/42"))
                .andExpect(header("Authorization", "Bearer worker-token"))
                .andRespond(withSuccess("[{\"zone\":\"A\"}]", MediaType.APPLICATION_JSON));

        assertThat(client.zoneDetails(42)).isEqualTo("[{\"zone\":\"A\"}]");
        experimentService.verify();
    }

    @Test
    void parcelsComeFromTheParcelOwner() {
        parcelService.expect(once(), requestTo("http://parcel.test/api/parcels/getParcels/42"))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        assertThat(client.parcels(42)).isEqualTo("[]");
        parcelService.verify();
    }

    @Test
    void on401TheWorkerLogsInAgainOnce() {
        experimentService.expect(once(), requestTo("http://experiment.test/api/resourceDetails/getInfeedDetails/42"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));
        experimentService.expect(once(), requestTo("http://experiment.test/api/resourceDetails/getInfeedDetails/42"))
                .andRespond(withSuccess("[1]", MediaType.APPLICATION_JSON));

        assertThat(client.infeedDetails(42)).isEqualTo("[1]");
        verify(tokenProvider).invalidate();
    }
}
