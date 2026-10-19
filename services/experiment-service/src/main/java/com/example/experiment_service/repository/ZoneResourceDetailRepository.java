package com.example.experiment_service.repository;

import com.example.experiment_service.entity.ZoneResourceDetail;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ZoneResourceDetailRepository extends JpaRepository<ZoneResourceDetail, Long> {

    /** All rows of one experiment. "Experiment_ExperimentId" = field experiment, then its experimentId. */
    List<ZoneResourceDetail> findByExperiment_ExperimentId(Long experimentId, Sort sort);

    /**
     * Deletes all rows of one experiment with ONE delete statement.
     * @Modifying = this query changes data. Call it inside a @Transactional service method.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from ZoneResourceDetail d where d.experiment.experimentId = :experimentId")
    void deleteByExperimentId(@Param("experimentId") Long experimentId);
}