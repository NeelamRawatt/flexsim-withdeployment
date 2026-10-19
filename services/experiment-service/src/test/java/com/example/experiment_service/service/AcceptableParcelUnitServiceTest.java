package com.example.experiment_service.service;

import static com.example.experiment_service.testsupport.StatusAssert.assertStatus;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.experiment_service.dto.AcceptableParcelUnitDto;
import com.example.experiment_service.entity.AcceptableParcelUnit;
import com.example.experiment_service.entity.Experiment;
import com.example.experiment_service.repository.AcceptableParcelUnitRepository;
import com.example.experiment_service.security.AuthenticatedUser;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class AcceptableParcelUnitServiceTest {

    private static final Long EXP_ID = 1001L;
    private static final AuthenticatedUser USER = new AuthenticatedUser(5L, "owner", "OPERATOR");

    @Mock
    private ExperimentService experimentService;
    @Mock
    private AcceptableParcelUnitRepository repository;

    private AcceptableParcelUnitService service;

    @BeforeEach
    void setUp() {
        service = new AcceptableParcelUnitService(experimentService, repository);
    }

    private static AcceptableParcelUnitDto limits(Double minH, Double maxH, Double minW, Double maxW) {
        return new AcceptableParcelUnitDto(EXP_ID, minH, maxH, 1.0, 100.0, minW, maxW, 0.5, 30.0);
    }

    @Test
    void goodLimitsReplaceTheOldRow() {
        Experiment experiment = new Experiment();
        when(experimentService.findOwnedOrThrow(EXP_ID, USER)).thenReturn(experiment);

        service.save(limits(1.0, 50.0, 1.0, 80.0), USER);

        InOrder order = inOrder(repository);
        order.verify(repository).deleteByExperimentId(EXP_ID);
        ArgumentCaptor<AcceptableParcelUnit> saved = ArgumentCaptor.forClass(AcceptableParcelUnit.class);
        order.verify(repository).save(saved.capture());
        assertThat(saved.getValue().getExperiment()).isSameAs(experiment);
        assertThat(saved.getValue().getMaxHeight()).isEqualTo(50.0);
        assertThat(saved.getValue().getMaxWeight()).isEqualTo(30.0);
    }

    @Test
    void minEqualToMaxIsAllowed() {
        when(experimentService.findOwnedOrThrow(EXP_ID, USER)).thenReturn(new Experiment());

        service.save(limits(5.0, 5.0, 1.0, 80.0), USER);
    }

    @Test
    void missingBodyOrExperimentIdGives400() {
        assertStatus(() -> service.save(null, USER), HttpStatus.BAD_REQUEST);
        assertStatus(() -> service.save(new AcceptableParcelUnitDto(null, 1.0, 2.0, 1.0, 2.0, 1.0, 2.0, 1.0, 2.0), USER),
                HttpStatus.BAD_REQUEST);
        verifyNoInteractions(experimentService, repository);
    }

    @Test
    void negativeNumberGives400() {
        assertStatus(() -> service.save(limits(-1.0, 5.0, 1.0, 80.0), USER), HttpStatus.BAD_REQUEST);
        verifyNoInteractions(repository);
    }

    @Test
    void notANumberGives400() {
        assertStatus(() -> service.save(limits(Double.NaN, 5.0, 1.0, 80.0), USER), HttpStatus.BAD_REQUEST);
        verifyNoInteractions(repository);
    }

    @Test
    void minAboveMaxGives400() {
        assertStatus(() -> service.save(limits(10.0, 5.0, 1.0, 80.0), USER), HttpStatus.BAD_REQUEST);
        verifyNoInteractions(repository);
    }

    @Test
    void notTheOwnerGives403() {
        when(experimentService.findOwnedOrThrow(EXP_ID, USER))
                .thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "no"));

        assertStatus(() -> service.save(limits(1.0, 5.0, 1.0, 80.0), USER), HttpStatus.FORBIDDEN);
        verifyNoInteractions(repository);
    }

    @Test
    void readGivesEmptyWhenNothingWasSaved() {
        when(repository.findTopByExperiment_ExperimentIdOrderByIdDesc(EXP_ID)).thenReturn(Optional.empty());

        assertThat(service.getByExperimentId(EXP_ID)).isEmpty();
    }

    @Test
    void readGivesTheSavedLimits() {
        AcceptableParcelUnit entity = new AcceptableParcelUnit();
        entity.setMinHeight(1.0);
        entity.setMaxHeight(50.0);
        entity.setMaxWeight(30.0);
        when(repository.findTopByExperiment_ExperimentIdOrderByIdDesc(EXP_ID)).thenReturn(Optional.of(entity));

        Optional<AcceptableParcelUnitDto> result = service.getByExperimentId(EXP_ID);

        assertThat(result).isPresent();
        assertThat(result.get().experimentId()).isEqualTo(EXP_ID);
        assertThat(result.get().maxHeight()).isEqualTo(50.0);
        assertThat(result.get().maxWeight()).isEqualTo(30.0);
    }
}