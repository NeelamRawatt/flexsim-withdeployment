package com.example.flexsim_worker.kpi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.flexsim_worker.job.ActiveRun;
import com.example.flexsim_worker.job.ActiveRuns;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import tools.jackson.databind.JsonNode;

class KpiPublisherTest {

    private KafkaTemplate<String, String> kafka;
    private KpiPublisher publisher;
    private ActiveRun run;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        kafka = mock(KafkaTemplate.class);
        publisher = new KpiPublisher(kafka, Clock.fixed(Instant.parse("2026-11-15T05:00:00Z"), ZoneOffset.UTC), 1);
        run = new ActiveRuns().start(7, 42);
    }

    @Test
    void snapshotIsSentWithRunIdAndKeyExperimentId() {
        when(kafka.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture((SendResult<String, String>) null));
        JsonNode kpi = publisher.read("{\"experimentId\":42,\"simulationTime\":3600.5,\"chutewiseThroughput\":{\"C1\":12}}");

        publisher.publish(run, kpi);

        ArgumentCaptor<String> value = ArgumentCaptor.forClass(String.class);
        verify(kafka).send(eq("simulation.kpi"), eq("42"), value.capture());
        assertThat(value.getValue())
                .contains("\"runId\":7", "\"experimentId\":42", "\"receivedAt\":\"2026-11-15T05:00:00Z\"",
                        "\"chutewiseThroughput\":{\"C1\":12}");
    }

    @Test
    void kafkaNotAnsweringIsAnError() {
        when(kafka.send(anyString(), anyString(), anyString())).thenReturn(new CompletableFuture<>());   // never answers
        JsonNode kpi = publisher.read("{\"experimentId\":42}");

        assertThatThrownBy(() -> publisher.publish(run, kpi)).isInstanceOf(KpiPublisher.KpiPublishException.class);
    }

    @Test
    void brokenKpiMessagesAreRefused() {
        assertThatThrownBy(() -> publisher.read("")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> publisher.read("not json")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> publisher.read("{\"simulationTime\":1}"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("experimentId");
    }
}
