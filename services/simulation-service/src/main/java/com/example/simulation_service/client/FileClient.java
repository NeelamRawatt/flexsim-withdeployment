package com.example.simulation_service.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

/**
 * Asks file-service (REST): "does this experiment have BOTH Excel files?"  -> true / false
 * Called before a run is queued. We send the USER'S OWN token, like the experiment check.
 *
 * file-service down / timeout -> 503 "try again", never a hang (timeouts in RestClientConfig).
 */
@Component
public class FileClient {

    private static final Logger log = LoggerFactory.getLogger(FileClient.class);

    private final RestClient restClient;

    public FileClient(RestClient fileRestClient) {
        this.restClient = fileRestClient;
    }

    public boolean filesReady(Long experimentId, String token) {
        try {
            Boolean ready = restClient.get()
                    .uri("/api/files/exists/{id}", experimentId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .retrieve()
                    .body(Boolean.class);
            return Boolean.TRUE.equals(ready);
        } catch (RestClientException e) {
            log.warn("Call to file-service failed for experiment {}: {}", experimentId, e.toString());
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "File service is not available. Please try again in a minute.");
        }
    }
}