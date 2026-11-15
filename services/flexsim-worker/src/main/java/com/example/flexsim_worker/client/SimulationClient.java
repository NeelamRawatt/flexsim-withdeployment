package com.example.flexsim_worker.client;

import com.example.flexsim_worker.auth.WorkerTokenProvider;
import java.util.Optional;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

/**
 * The worker's REST calls to simulation-service.
 *
 *   getRun(runId)            GET  /api/simulation/runs/{runId}          "is this run still waiting?"
 *   updateStatus(runId, ...) POST /api/simulation/runs/{runId}/status   "RUNNING / COMPLETED / FAILED"
 *
 * Every call carries the worker's token:  Authorization: Bearer <token>
 * (A program has no browser, so it sends the token in this header instead of a cookie.
 *  simulation-service accepts both: see JwtAuthenticationFilter.resolveToken.)
 *
 * Answers:
 *   found      -> Optional with the run
 *   404        -> Optional.empty()  = the run does not exist any more (the experiment was deleted).
 *                 The worker then just SKIPS this job.
 *   401        -> our token was not accepted: forget it, log in again, try ONE more time.
 *   409        -> thrown (HttpClientErrorException.Conflict): the change is not allowed,
 *                 for example COMPLETED -> RUNNING. W3 decides what to do.
 *   other / down -> thrown, so the caller knows the call did not work.
 */
@Component
public class SimulationClient {

    /** simulation-service accepts max 50 / 500 characters (see RunStatusUpdateRequest there). */
    static final int MAX_STAGE_LENGTH = 50;
    static final int MAX_MESSAGE_LENGTH = 500;

    private final RestClient simulationRestClient;
    private final WorkerTokenProvider tokenProvider;

    public SimulationClient(@Qualifier("simulationRestClient") RestClient simulationRestClient,
                            WorkerTokenProvider tokenProvider) {
        this.simulationRestClient = simulationRestClient;
        this.tokenProvider = tokenProvider;
    }

    public Optional<RunView> getRun(long runId) {
        return callWithLogin(() -> simulationRestClient.get()
                .uri("/api/simulation/runs/{runId}", runId)
                .headers(h -> h.setBearerAuth(tokenProvider.getToken()))
                .retrieve()
                .body(RunView.class));
    }

    public Optional<RunView> updateStatus(long runId, RunStatus status, String stage, String message) {
        StatusUpdate body = new StatusUpdate(status, cut(stage, MAX_STAGE_LENGTH), cut(message, MAX_MESSAGE_LENGTH));
        return callWithLogin(() -> simulationRestClient.post()
                .uri("/api/simulation/runs/{runId}/status", runId)
                .headers(h -> h.setBearerAuth(tokenProvider.getToken()))
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(RunView.class));
    }

    /** Runs the call. On 401: new login and ONE more try. On 404: empty. */
    private Optional<RunView> callWithLogin(Supplier<RunView> call) {
        try {
            return callOnce(call);
        } catch (HttpClientErrorException.Unauthorized e) {
            tokenProvider.invalidate();
            return callOnce(call);   // a second 401 is thrown: something is really wrong
        }
    }

    private static Optional<RunView> callOnce(Supplier<RunView> call) {
        try {
            return Optional.ofNullable(call.get());
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        }
    }

    /** Make a text short enough, so simulation-service does not answer 400. */
    static String cut(String text, int max) {
        if (text == null || text.length() <= max) {
            return text;
        }
        return text.substring(0, max);
    }

    /** The JSON we send: {"status":"RUNNING","stage":"OPENING_FLEXSIM","message":"Opening FlexSim"} */
    record StatusUpdate(RunStatus status, String stage, String message) {
    }
}
