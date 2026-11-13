package com.example.file_service.consumer;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.example.file_service.service.UploadedFileService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Unit test of the listener: no real Kafka, the service is a fake. */
@ExtendWith(MockitoExtension.class)
class ExperimentDeletedListenerTest {

    @Mock
    private UploadedFileService uploadedFileService;

    private ExperimentDeletedListener listener;

    @BeforeEach
    void setUp() {
        listener = new ExperimentDeletedListener(uploadedFileService);
    }

    private static ConsumerRecord<String, String> message(String value) {
        // topic, partition, offset, key, value
        return new ConsumerRecord<>("experiment.deleted", 2, 0L, "1006", value);
    }

    @Test
    void aValidMessageDeletesTheFilesOfThatExperiment() {
        listener.onExperimentDeleted(message(
                "{\"eventId\":\"5f1c\",\"experimentId\":1006,\"deletedBy\":\"developer\","
                        + "\"occurredAt\":\"2026-11-10T10:00:00Z\",\"someNewField\":\"ignored\"}"));

        verify(uploadedFileService).deleteAllForExperiment(1006L);
    }

    @Test
    void aBrokenMessageIsRejectedAndNothingIsDeleted() {
        assertThatThrownBy(() -> listener.onExperimentDeleted(message("this is not json")))
                .isInstanceOf(InvalidEventException.class);
        assertThatThrownBy(() -> listener.onExperimentDeleted(message("{\"eventId\":\"x\"}")))
                .isInstanceOf(InvalidEventException.class)
                .hasMessageContaining("no experimentId");
        assertThatThrownBy(() -> listener.onExperimentDeleted(message("")))
                .isInstanceOf(InvalidEventException.class);

        verifyNoInteractions(uploadedFileService);
    }
}