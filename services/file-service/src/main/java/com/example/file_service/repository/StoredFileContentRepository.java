package com.example.file_service.repository;

import com.example.file_service.entity.StoredFileContent;
import org.springframework.data.jpa.repository.JpaRepository;

/** Reads and writes the file_content table (the bytes). Used only by DatabaseFileStorage. */
public interface StoredFileContentRepository extends JpaRepository<StoredFileContent, String> {
}