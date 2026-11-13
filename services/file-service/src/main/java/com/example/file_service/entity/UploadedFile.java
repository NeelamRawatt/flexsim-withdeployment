package com.example.file_service.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import com.example.file_service.enums.FileCategory;
import com.example.file_service.enums.StorageType;

/**
 * The DETAILS of one uploaded file (name, size, who, when, where it is stored).
 * The file CONTENT itself is NOT in this row: it is kept by the storage (see StorageType),
 * found by storageKey. So listing files never loads megabytes of data by accident.
 */
@Entity
@Table(name = "uploaded_file")
public class UploadedFile {

    public UploadedFile() {
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // The experiment (lives in experiment-service). Plain number, no foreign key (other database).
    @Column(nullable = false)
    private Long experimentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private FileCategory fileCategory;

    @Column(nullable = false)
    private String fileName;

    @Column(length = 20)
    private String fileExtension;

    @Column(length = 100)
    private String contentType;

    @Column(nullable = false)
    private Long sizeBytes;

    // A "fingerprint" of the content. Lets us prove later that the file was not changed.
    @Column(nullable = false, length = 64)
    private String sha256;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StorageType storageType;

    // The address of the content inside the storage (later: the blob name in Azure).
    @Column(nullable = false, unique = true)
    private String storageKey;

    // The date the user chose for the file (from the upload form).
    private LocalDate uploadDate;

    @Column(nullable = false)
    private LocalDateTime uploadedAt;

    // Who uploaded it (from the token).
    @Column(nullable = false)
    private String uploadedBy;

    private Long uploadedByUserId;

    @PrePersist
    void beforeFirstSave() {
        if (uploadedAt == null) {
            uploadedAt = LocalDateTime.now(ZoneOffset.UTC);
        }
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getExperimentId() { return experimentId; }
    public void setExperimentId(Long experimentId) { this.experimentId = experimentId; }

    public FileCategory getFileCategory() { return fileCategory; }
    public void setFileCategory(FileCategory fileCategory) { this.fileCategory = fileCategory; }

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }

    public String getFileExtension() { return fileExtension; }
    public void setFileExtension(String fileExtension) { this.fileExtension = fileExtension; }

    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }

    public Long getSizeBytes() { return sizeBytes; }
    public void setSizeBytes(Long sizeBytes) { this.sizeBytes = sizeBytes; }

    public String getSha256() { return sha256; }
    public void setSha256(String sha256) { this.sha256 = sha256; }

    public StorageType getStorageType() { return storageType; }
    public void setStorageType(StorageType storageType) { this.storageType = storageType; }

    public String getStorageKey() { return storageKey; }
    public void setStorageKey(String storageKey) { this.storageKey = storageKey; }

    public LocalDate getUploadDate() { return uploadDate; }
    public void setUploadDate(LocalDate uploadDate) { this.uploadDate = uploadDate; }

    public LocalDateTime getUploadedAt() { return uploadedAt; }
    public void setUploadedAt(LocalDateTime uploadedAt) { this.uploadedAt = uploadedAt; }

    public String getUploadedBy() { return uploadedBy; }
    public void setUploadedBy(String uploadedBy) { this.uploadedBy = uploadedBy; }

    public Long getUploadedByUserId() { return uploadedByUserId; }
    public void setUploadedByUserId(Long uploadedByUserId) { this.uploadedByUserId = uploadedByUserId; }
}