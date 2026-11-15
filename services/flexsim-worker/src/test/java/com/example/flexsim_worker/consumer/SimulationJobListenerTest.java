package com.example.flexsim_worker.consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.example.flexsim_worker.event.SimulationTriggerRequestedEvent;
import com.example.flexsim_worker.job.SimulationJobRunner;
import org.junit.jupiter.api.Test;

class SimulationJobListenerTest {

    private final SimulationJobListener listener = new SimulationJobListener(mock(SimulationJobRunner.class));

    @Test
    void readsTheJobMessageFromSimulationService() {
        String json = """
                {"eventId":"e1","runId":7,"experimentId":42,"experimentName":"Pune test",
                 "startTime":"2026-10-21T23:00:00","endTime":"2026-10-21T06:30:00","maxRecirculationCount":3,
                 "useCaseId":2,"sorterFullPercent":90,"requestedBy":"neelam","occurredAt":"2026-11-15T05:00:00Z",
                 "aFieldAddedLater":"is ignored"}
                """;

        SimulationTriggerRequestedEvent job = listener.parse(json);

        assertThat(job.runId()).isEqualTo(7L);
        assertThat(job.experimentId()).isEqualTo(42L);
        assertThat(job.useCaseId()).isEqualTo(2);
        assertThat(job.flexibleResources()).isNull();
    }

    @Test
    void brokenMessagesGoToTheDeadLetterTopic() {
        assertThatThrownBy(() -> listener.parse("")).isInstanceOf(InvalidEventException.class);
        assertThatThrownBy(() -> listener.parse("not json")).isInstanceOf(InvalidEventException.class);
        assertThatThrownBy(() -> listener.parse("{\"experimentId\":42}"))
                .isInstanceOf(InvalidEventException.class).hasMessageContaining("runId");
    }
}
