package com.example.simulation_service.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.example.simulation_service.security.AuthenticatedUser;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

/**
 * Tests the call to experiment-service WITHOUT a real experiment-service.
 * MockRestServiceServer = a fake server inside the test: we tell it which request to expect
 * and which answer to give.
 */
class ExperimentClientTest {

    private static final AuthenticatedUser NEELAM = new AuthenticatedUser(5L, "neelam", "OPERATOR");
    private static final String URL = "http://experiment/api/experiments/1005";

    private MockRestServiceServer server;
    private ExperimentClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://experiment");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new ExperimentClient(builder.build());
    }

    private static void assertStatus(ThrowingCallable call, HttpStatus expected) {
        assertThatThrownBy(call).isInstanceOfSatisfying(ResponseStatusException.class,
                e -> assertThat(e.getStatusCode()).isEqualTo(expected));
    }

    @Test
    void theOwnerPassesAndTheUsersTokenIsSentOn() {
        server.expect(requestTo(URL))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer user-token"))
                .andRespond(withSuccess("{\"experimentId\":1005,\"username\":\"neelam\",\"experimentName\":\"Pune test\"}",
                        MediaType.APPLICATION_JSON));

        assertThat(client.requireOwnedBy(1005L, NEELAM, "user-token").experimentId()).isEqualTo(1005L);
        server.verify();
    }

    @Test
    void anotherUsersExperimentGives403() {
        server.expect(requestTo(URL))
                .andRespond(withSuccess("{\"experimentId\":1005,\"username\":\"bob\"}", MediaType.APPLICATION_JSON));

        assertStatus(() -> client.requireOwnedBy(1005L, NEELAM, "user-token"), HttpStatus.FORBIDDEN);
    }

    @Test
    void unknownExperimentGives404() {
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertStatus(() -> client.requireOwnedBy(1005L, NEELAM, "user-token"), HttpStatus.NOT_FOUND);
    }

    @Test
    void refusedByExperimentServiceGives403() {
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.FORBIDDEN));

        assertStatus(() -> client.requireOwnedBy(1005L, NEELAM, "user-token"), HttpStatus.FORBIDDEN);
    }

    @Test
    void experimentServiceProblemGives503() {
        server.expect(requestTo(URL)).andRespond(withServerError());

        assertStatus(() -> client.requireOwnedBy(1005L, NEELAM, "user-token"), HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void noTokenGives401WithoutCallingExperimentService() {
        assertStatus(() -> client.requireOwnedBy(1005L, NEELAM, null), HttpStatus.UNAUTHORIZED);
        server.verify();   // no request was expected, and none was made
    }
}