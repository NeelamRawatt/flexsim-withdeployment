package com.example.experiment_service.repository;

import com.example.experiment_service.entity.ResourceTemplateRow;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ResourceTemplateRowRepository extends JpaRepository<ResourceTemplateRow, Long> {

    List<ResourceTemplateRow> findByTemplate_IdOrderByRowIndex(Long templateId);

    /**
     * The rows of MANY templates in ONE query (instead of one query per template).
     * This avoids the "N+1 problem": 1 query for the templates + N queries for their rows.
     */
    List<ResourceTemplateRow> findByTemplate_IdInOrderByTemplate_IdAscRowIndexAsc(Collection<Long> templateIds);
}