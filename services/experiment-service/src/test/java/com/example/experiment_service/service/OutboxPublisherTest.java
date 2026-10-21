package com.example.experiment_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.experiment_service.entity.OutboxEvent;
import com.example.experiment_service.repository.OutboxEventRepository;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

/** Unit test of the sender. Kafka and the database are FAKES (mocks). */
@ExtendWith(MockitoExtension.class)
class OutboxPublisherTest {

    @Mock
    private OutboxEventRepository repository;
    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    private OutboxPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new OutboxPublisher(repository, kafkaTemplate, 100, 5, 7);
    }

    private static OutboxEvent row(String key) {
        OutboxEvent e = new OutboxEvent("experiment.deleted", key, "{\"experimentId\":" + key + "}");
        e.setAttempts(0);
        return e;
    }

    @Test
    void sentRowsGetSentAtFilled() {
        OutboxEvent first = row("1001");
        OutboxEvent second = row("1002");
        when(repository.lockNextUnsent(100)).thenReturn(List.of(first, second));
        when(kafkaTemplate.send("experiment.deleted", "1001", first.getPayload()))
                .thenReturn(CompletableFuture.completedFuture(null));
        when(kafkaTemplate.send("experiment.deleted", "1002", second.getPayload()))
                .thenReturn(CompletableFuture.completedFuture(null));

        publisher.publishPending();

        assertThat(first.getSentAt()).isNotNull();
        assertThat(second.getSentAt()).isNotNull();
    }

    @Test
    void whenKafkaFailsTheRowStaysUnsentAndTheRoundStopsToKeepTheOrder() {
        OutboxEvent first = row("1001");
        OutboxEvent second = row("1002");
        when(repository.lockNextUnsent(100)).thenReturn(List.of(first, second));
        when(kafkaTemplate.send("experiment.deleted", "1001", first.getPayload()))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("Kafka not reachable")));

        publisher.publishPending();

        assertThat(first.getSentAt()).isNull();                 // not sent -> will retry
        assertThat(first.getAttempts()).isEqualTo(1);
        assertThat(first.getLastError()).contains("Kafka not reachable");
        assertThat(second.getSentAt()).isNull();                // not even tried: order is kept
        verify(kafkaTemplate, never()).send("experiment.deleted", "1002", second.getPayload());
    }

    @Test
    void nothingToSendMeansNoKafkaCall() {
        when(repository.lockNextUnsent(100)).thenReturn(List.of());

        publisher.publishPending();

        verify(kafkaTemplate, never()).send(anyString(), anyString(), anyString());
    }
}