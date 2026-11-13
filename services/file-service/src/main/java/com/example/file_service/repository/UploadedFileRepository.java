package com.example.file_service.repository;

import com.example.file_service.entity.UploadedFile;
import com.example.file_service.enums.FileCategory;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Reads and writes uploaded_file (the DETAILS, never the bytes).
 * Spring Data writes the SQL for us from the method NAME, for example:
 *   existsByExperimentIdAndFileCategory -> SELECT ... WHERE experiment_id = ? AND file_category = ?
 */
public interface UploadedFileRepository extends JpaRepository<UploadedFile, Long> {

    Optional<UploadedFile> findByExperimentIdAndFileCategory(Long experimentId, FileCategory fileCategory);

    List<UploadedFile> findByExperimentIdOrderByFileCategoryAsc(Long experimentId);

    boolean existsByExperimentIdAndFileCategory(Long experimentId, FileCategory fileCategory);
}