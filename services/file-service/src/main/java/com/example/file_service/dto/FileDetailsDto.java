package com.example.file_service.dto;

import com.example.file_service.entity.UploadedFile;
import com.example.file_service.enums.FileCategory;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * The DETAILS of one file, as React sees them. No bytes, and no storage address
 * (storageKey is internal: the outside world does not need to know where we keep files).
 */
public record FileDetailsDto(
        Long id,
        Long experimentId,
        FileCategory fileCategory,
        String fileName,
        Long sizeBytes,
        String sha256,
        LocalDate uploadDate,
        LocalDateTime uploadedAt,
        String uploadedBy) {

    public static FileDetailsDto from(UploadedFile file) {
        return new FileDetailsDto(
                file.getId(),
                file.getExperimentId(),
                file.getFileCategory(),
                file.getFileName(),
                file.getSizeBytes(),
                file.getSha256(),
                file.getUploadDate(),
                file.getUploadedAt(),
                file.getUploadedBy());
    }
}