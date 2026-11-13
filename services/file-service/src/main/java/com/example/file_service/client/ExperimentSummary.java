package com.example.file_service.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * The 2 fields WE need from experiment-service's answer to GET /api/experiments/{id}.
 * The answer has ~25 more fields; ignoreUnknown = true tells Jackson to skip them.
 * username = the OWNER of the experiment (who created it).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ExperimentSummary(Long experimentId, String username) {
}