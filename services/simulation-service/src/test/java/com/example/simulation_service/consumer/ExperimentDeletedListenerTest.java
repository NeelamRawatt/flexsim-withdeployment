package com.example.simulation_service.consumer;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.example.simulation_service.service.SimulationRunCleanupService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ExperimentDeletedListenerTest {

    @Mock
    private SimulationRunCleanupService cleanupService;

    private static ConsumerRecord<String, String> message(String value) {
        return new ConsumerRecord<>("experiment.deleted", 1, 0L, "1008", value);
    }

    @Test
    void aValidMessageDeletesTheRunsOfThatExperiment() {
        new ExperimentDeletedListener(cleanupService).onExperimentDeleted(message(
                "{\"eventId\":\"x\",\"experimentId\":1008,\"deletedBy\":\"developer\",\"newField\":1}"));

        verify(cleanupService).deleteAllRunsOfExperiment(1008L);
    }

    @Test
    void aBrokenMessageIsRejectedAndNothingIsDeleted() {
        ExperimentDeletedListener listener = new ExperimentDeletedListener(cleanupService);

        assertThatThrownBy(() -> listener.onExperimentDeleted(message("not json")))
                .isInstanceOf(InvalidEventException.class);
        assertThatThrownBy(() -> listener.onExperimentDeleted(message("{\"eventId\":\"x\"}")))
                .isInstanceOf(InvalidEventException.class);
        verifyNoInteractions(cleanupService);
    }
}
