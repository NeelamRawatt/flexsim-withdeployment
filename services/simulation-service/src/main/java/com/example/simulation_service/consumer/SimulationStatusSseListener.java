package com.example.simulation_service.consumer;

import com.example.simulation_service.event.KafkaTopics;
import com.example.simulation_service.event.SimulationStatusEvent;
import com.example.simulation_service.sse.ProgressStreamHub;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Reads "simulation.status" from Kafka and pushes each message to the browsers that THIS copy holds.
 *
 * Why read our OWN topic? With 2+ copies of simulation-service (Kubernetes), the status update may
 * arrive at copy A while the browser is connected to copy B. Through Kafka, EVERY copy gets EVERY message:
 *
 *   groupId = "simulation-service-sse-<random>"  -> each copy has its OWN consumer group,
 *                                                  so each copy receives all messages (like a broadcast).
 *   auto.offset.reset = latest                  -> only NEW messages (old ones are not "live" news).
 *
 * A broken message is only logged and skipped: this is screen news, not business data.
 */
@Component
public class SimulationStatusSseListener {

    private static final Logger log = LoggerFactory.getLogger(SimulationStatusSseListener.class);

    private final ProgressStreamHub hub;
    private final JsonMapper json = JsonMapper.builder().build();

    public SimulationStatusSseListener(ProgressStreamHub hub) {
        this.hub = hub;
    }

    @KafkaListener(
            topics = KafkaTopics.SIMULATION_STATUS,
            groupId = "simulation-service-sse-${random.uuid}",
            properties = "auto.offset.reset=latest")
    public void onStatus(String value) {
        SimulationStatusEvent event;
        try {
            event = json.readValue(value, SimulationStatusEvent.class);
        } catch (JacksonException e) {
            log.warn("Skipping a simulation.status message that is not valid JSON");
            return;
        }
        if (event.experimentId() == null) {
            log.warn("Skipping a simulation.status message without experimentId");
            return;
        }
        hub.send(event);
    }
}