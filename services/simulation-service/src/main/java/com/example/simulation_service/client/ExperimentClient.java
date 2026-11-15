package com.example.simulation_service.client;

import com.example.simulation_service.security.AuthenticatedUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

/**
 * Asks experiment-service (REST) about an experiment, BEFORE we start a simulation.
 *
 * We send the USER'S OWN token (not a service password). So experiment-service checks
 * the same person with its own rules, and file-service can never see more than the user may.
 *
 * Answers of experiment-service -> what WE answer:
 *   200            -> ok, we read the owner
 *   404            -> 404 "Experiment not found"
 *   401 / 403      -> 403 "not allowed"
 *   down / timeout -> 503 "try again in a minute"  (we never hang: timeouts in RestClientConfig)
 */
@Component
public class ExperimentClient {

    private static final Logger log = LoggerFactory.getLogger(ExperimentClient.class);

    private final RestClient restClient;

    public ExperimentClient(RestClient experimentRestClient) {
        this.restClient = experimentRestClient;
    }

    /** The experiment must exist AND belong to this user (only the owner may run it). */
    public ExperimentSummary requireOwnedBy(Long experimentId, AuthenticatedUser user, String token) {
        ExperimentSummary experiment = fetch(experimentId, token);
        if (experiment.username() == null || !experiment.username().equals(user.username())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "You are not allowed to run this experiment");
        }
        return experiment;
    }

    ExperimentSummary fetch(Long experimentId, String token) {
        if (token == null || token.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Login token is missing");
        }
        try {
            ExperimentSummary body = restClient.get()
                    .uri("/api/experiments/{id}", experimentId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .retrieve()
                    .body(ExperimentSummary.class);
            if (body == null) {
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                        "Experiment service gave an empty answer. Please try again.");
            }
            return body;
        } catch (HttpClientErrorException.NotFound e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Experiment not found with id: " + experimentId);
        } catch (HttpClientErrorException.Unauthorized | HttpClientErrorException.Forbidden e) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You are not allowed to access this experiment");
        } catch (RestClientException e) {
            // Down, timeout, 500 ... Details go to OUR log only.
            log.warn("Call to experiment-service failed for experiment {}: {}", experimentId, e.toString());
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Experiment service is not available. Please try again in a minute.");
        }
    }
}