package com.example.flexsim_worker.kpi;

import com.example.flexsim_worker.event.KafkaTopics;
import com.example.flexsim_worker.job.ActiveRun;
import java.time.Clock;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Sends the KPI snapshots from FlexSim to Kafka (topic simulation.kpi). kpi-service saves them.
 *
 * Why Kafka and not a REST call to kpi-service:
 *   - kpi-service can be restarted or be slow: the snapshots wait safely in Kafka.
 *   - the worker does not need to know where kpi-service runs.
 *
 * key = experimentId -> all snapshots of one experiment stay IN ORDER (same partition).
 * We WAIT for Kafka's "received" (max 10 s). If it does not come, FlexSim gets an error answer,
 * so nothing is lost silently.
 *
 * The worker has no database, so there is no outbox here: the KPI is sent directly.
 */
@Component
public class KpiPublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final Clock clock;
    private final long sendTimeoutSeconds;
    private final JsonMapper json = JsonMapper.builder().build();

    public KpiPublisher(KafkaTemplate<String, String> kafkaTemplate, Clock clock,
                        @Value("${app.kpi.send-timeout-seconds:10}") long sendTimeoutSeconds) {
        this.kafkaTemplate = kafkaTemplate;
        this.clock = clock;
        this.sendTimeoutSeconds = sendTimeoutSeconds;
    }

    /** Reads the KPI text from FlexSim. Not valid JSON, or no experimentId -> IllegalArgumentException. */
    public JsonNode read(String body) {
        if (body == null || body.isBlank()) {
            throw new IllegalArgumentException("Empty KPI message");
        }
        JsonNode kpi;
        try {
            kpi = json.readTree(body);
        } catch (JacksonException e) {
            throw new IllegalArgumentException("KPI message is not valid JSON");
        }
        JsonNode experimentId = kpi.get("experimentId");
        if (experimentId == null || !experimentId.isNumber()) {
            throw new IllegalArgumentException("KPI message has no experimentId");
        }
        return kpi;
    }

    public static long experimentIdOf(JsonNode kpi) {
        return kpi.get("experimentId").longValue();
    }

    /** Sends one snapshot and waits for Kafka's "received". Problems -> KpiPublishException. */
    public void publish(ActiveRun run, JsonNode kpi) {
        SimulationKpiEvent event = new SimulationKpiEvent(UUID.randomUUID().toString(), run.runId(),
                run.experimentId(), clock.instant().toString(), kpi);
        String value = json.writeValueAsString(event);
        try {
            kafkaTemplate.send(KafkaTopics.SIMULATION_KPI, String.valueOf(run.experimentId()), value)
                    .get(sendTimeoutSeconds, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new KpiPublishException("Interrupted while sending the KPI", e);
        } catch (ExecutionException | TimeoutException e) {
            throw new KpiPublishException("Kafka did not accept the KPI: " + e.getMessage(), e);
        }
    }

    public static class KpiPublishException extends RuntimeException {
        public KpiPublishException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
