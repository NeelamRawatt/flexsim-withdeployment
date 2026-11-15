package com.example.simulation_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.example.simulation_service.repository.SimulationRunRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SimulationRunCleanupServiceTest {

    @Mock
    private SimulationRunRepository repository;

    @Test
    void returnsHowManyRunsWereDeleted() {
        when(repository.deleteByExperimentId(1008L)).thenReturn(3).thenReturn(0);
        SimulationRunCleanupService service = new SimulationRunCleanupService(repository);

        assertThat(service.deleteAllRunsOfExperiment(1008L)).isEqualTo(3);
        assertThat(service.deleteAllRunsOfExperiment(1008L)).isZero();   // duplicate message: no harm
    }
}
