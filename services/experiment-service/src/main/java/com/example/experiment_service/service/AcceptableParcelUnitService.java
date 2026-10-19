package com.example.experiment_service.service;

import com.example.experiment_service.dto.AcceptableParcelUnitDto;
import com.example.experiment_service.entity.AcceptableParcelUnit;
import com.example.experiment_service.entity.Experiment;
import com.example.experiment_service.repository.AcceptableParcelUnitRepository;
import com.example.experiment_service.security.AuthenticatedUser;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Saves and reads the allowed parcel size and weight limits of an experiment.
 *
 * SAVE = REPLACE: one row per experiment (the old row is deleted, the new one inserted, in one transaction).
 * New safety checks (the old app had none): no negative numbers, and a minimum may not be above its maximum.
 */
@Service
public class AcceptableParcelUnitService {

    private final ExperimentService experimentService;
    private final AcceptableParcelUnitRepository repository;

    public AcceptableParcelUnitService(ExperimentService experimentService, AcceptableParcelUnitRepository repository) {
        this.experimentService = experimentService;
        this.repository = repository;
    }

    @Transactional
    public void save(AcceptableParcelUnitDto dto, AuthenticatedUser user) {
        if (dto == null || dto.experimentId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "experimentId is required");
        }
        checkPair(dto.minHeight(), dto.maxHeight(), "height");
        checkPair(dto.minLength(), dto.maxLength(), "length");
        checkPair(dto.minWidth(), dto.maxWidth(), "width");
        checkPair(dto.minWeight(), dto.maxWeight(), "weight");

        Experiment experiment = experimentService.findOwnedOrThrow(dto.experimentId(), user);

        AcceptableParcelUnit entity = new AcceptableParcelUnit();
        entity.setExperiment(experiment);
        entity.setMinHeight(dto.minHeight());
        entity.setMaxHeight(dto.maxHeight());
        entity.setMinLength(dto.minLength());
        entity.setMaxLength(dto.maxLength());
        entity.setMinWidth(dto.minWidth());
        entity.setMaxWidth(dto.maxWidth());
        entity.setMinWeight(dto.minWeight());
        entity.setMaxWeight(dto.maxWeight());

        repository.deleteByExperimentId(dto.experimentId());
        repository.save(entity);
    }

    /** Returns empty when nothing was saved yet (the controller then answers 204 No Content, like the old app). */
    @Transactional(readOnly = true)
    public Optional<AcceptableParcelUnitDto> getByExperimentId(Long experimentId) {
        experimentService.requireExists(experimentId);
        return repository.findTopByExperiment_ExperimentIdOrderByIdDesc(experimentId)
                .map(e -> new AcceptableParcelUnitDto(
                        experimentId,
                        e.getMinHeight(), e.getMaxHeight(),
                        e.getMinLength(), e.getMaxLength(),
                        e.getMinWidth(), e.getMaxWidth(),
                        e.getMinWeight(), e.getMaxWeight()));
    }

    private void checkPair(Double min, Double max, String name) {
        if ((min != null && (min.isNaN() || min < 0)) || (max != null && (max.isNaN() || max < 0))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, name + " cannot be negative");
        }
        if (min != null && max != null && min > max) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "min " + name + " cannot be above max " + name);
        }
    }
}