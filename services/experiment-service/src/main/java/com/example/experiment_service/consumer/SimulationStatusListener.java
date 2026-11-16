package com.example.experiment_service.consumer;

import com.example.experiment_service.event.KafkaTopics;
import com.example.experiment_service.event.SimulationStatusEvent;
import com.example.experiment_service.service.ExperimentRunStatusService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * The CONSUMER of "simulation.status" in experiment-service:
 * keeps the small "latest run" copy on the experiment up to date (status, stage, message),
 * so the experiment HISTORY page shows RUNNING / COMPLETED / FAILED without asking simulation-service.
 *
 * Consumer group "experiment-service": one group for all copies, each message handled ONCE.
 * Key = experimentId, so all messages of one experiment arrive IN ORDER (same partition).
 * Errors: broken message -> straight to simulation.status.DLT; database down -> 3 retries, then DLT.
 */
@Component
public class SimulationStatusListener {

    private static final Logger log = LoggerFactory.getLogger(SimulationStatusListener.class);

    private final ExperimentRunStatusService statusService;
    private final JsonMapper json = JsonMapper.builder().build();

    public SimulationStatusListener(ExperimentRunStatusService statusService) {
        this.statusService = statusService;
    }

    @KafkaListener(topics = KafkaTopics.SIMULATION_STATUS)
    public void onStatus(ConsumerRecord<String, String> record) {
        SimulationStatusEvent event = parse(record.value());
        boolean changed = statusService.apply(event);
        log.debug("simulation.status run {} {} for experiment {}: {}", event.runId(), event.status(),
                event.experimentId(), changed ? "saved" : "skipped (old or already final)");
    }

    SimulationStatusEvent parse(String value) {
        if (value == null || value.isBlank()) {
            throw new InvalidEventException("Empty simulation.status message");
        }
        SimulationStatusEvent event;
        try {
            event = json.readValue(value, SimulationStatusEvent.class);
        } catch (JacksonException e) {
            throw new InvalidEventException("simulation.status message is not valid JSON", e);
        }
        if (event.experimentId() == null || event.runId() == null || event.status() == null) {
            throw new InvalidEventException("simulation.status message has no experimentId, runId or status");
        }
        return event;
    }
}
