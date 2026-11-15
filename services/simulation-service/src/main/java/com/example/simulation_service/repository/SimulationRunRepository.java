package com.example.simulation_service.repository;

import com.example.simulation_service.entity.SimulationRun;
import com.example.simulation_service.enums.RunStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Reads and writes simulation_run. Spring Data writes the SQL from the method names. */
public interface SimulationRunRepository extends JpaRepository<SimulationRun, Long> {

    /** "Is there already a QUEUED or RUNNING run for this experiment?" */
    boolean existsByExperimentIdAndStatusIn(Long experimentId, Collection<RunStatus> statuses);

    List<SimulationRun> findByExperimentIdOrderByQueuedAtDesc(Long experimentId);

    Optional<SimulationRun> findFirstByExperimentIdOrderByQueuedAtDesc(Long experimentId);

    /** Deletes ALL runs of one experiment with ONE SQL statement. Returns how many rows were deleted. */
    @Modifying
    @Query("delete from SimulationRun r where r.experimentId = :experimentId")
    int deleteByExperimentId(@Param("experimentId") Long experimentId);
}
