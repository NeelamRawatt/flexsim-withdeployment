package com.example.kpi_service.repository;

import com.example.kpi_service.entity.KpiSnapshot;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface KpiSnapshotRepository extends JpaRepository<KpiSnapshot, Long> {

    /** "Did we save this Kafka message already?" (the same message can come twice) */
    boolean existsByEventId(String eventId);

    /** All snapshots of one experiment, in simulation time order (uses the index). */
    List<KpiSnapshot> findByExperimentIdOrderBySimulationTimeAscIdAsc(Long experimentId);

    /** Experiment deleted -> its KPIs go too. ONE delete statement, not one per row. */
    @Modifying
    @Query("delete from KpiSnapshot k where k.experimentId = :experimentId")
    int deleteByExperimentId(@Param("experimentId") Long experimentId);
}
