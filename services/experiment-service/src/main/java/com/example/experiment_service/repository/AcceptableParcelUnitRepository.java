package com.example.experiment_service.repository;

import com.example.experiment_service.entity.AcceptableParcelUnit;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AcceptableParcelUnitRepository extends JpaRepository<AcceptableParcelUnit, Long> {

    /** The newest limits row of an experiment (highest id), if any. */
    Optional<AcceptableParcelUnit> findTopByExperiment_ExperimentIdOrderByIdDesc(Long experimentId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from AcceptableParcelUnit d where d.experiment.experimentId = :experimentId")
    void deleteByExperimentId(@Param("experimentId") Long experimentId);
}