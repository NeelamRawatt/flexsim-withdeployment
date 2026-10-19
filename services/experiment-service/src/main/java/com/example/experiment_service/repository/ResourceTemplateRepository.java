package com.example.experiment_service.repository;

import com.example.experiment_service.entity.ResourceTemplate;
import com.example.experiment_service.enums.TemplateType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ResourceTemplateRepository extends JpaRepository<ResourceTemplate, Long> {

    /** Templates of one user, newest first. */
    List<ResourceTemplate> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<ResourceTemplate> findByUserIdAndTemplateTypeOrderByCreatedAtDesc(Long userId, TemplateType templateType);

    /** Finds a template ONLY if it belongs to this user (stops one user opening another's template). */
    Optional<ResourceTemplate> findByIdAndUserId(Long id, Long userId);

    List<ResourceTemplate> findByTemplateTypeOrderByCreatedAtDesc(TemplateType templateType);

    /**
     * Deletes ONE template with ONE SQL statement. Its rows are removed by the database itself
     * (ON DELETE CASCADE in V1). The normal repository delete would first load every row into
     * memory and delete them one by one, which is slow for a template with thousands of rows.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from ResourceTemplate t where t.id = :id")
    int deleteTemplateById(@Param("id") Long id);
}