package com.example.experiment_service.repository;

import com.example.experiment_service.entity.Experiment;
import com.example.experiment_service.enums.ExperimentRunStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * The "repository" = the class we use to read and write experiments.
 * We only write an INTERFACE. Spring Data builds the working class for us at start-up.
 * JpaRepository already gives: save, findById, findAll, delete, count, paging.
 *
 * "Derived query": Spring reads the METHOD NAME and builds the SQL itself.
 *   findByUserId  ->  select * from experiment where user_id = ?
 */
public interface ExperimentRepository extends JpaRepository<Experiment, Long> {

    /** History of one user, one page at a time (database does the paging and sorting). */
    Page<Experiment> findByUserId(Long userId, Pageable pageable);

    /** History filtered by the latest run status (one query, uses the index on current_run_status). */
    Page<Experiment> findByCurrentRunStatus(ExperimentRunStatus status, Pageable pageable);
}