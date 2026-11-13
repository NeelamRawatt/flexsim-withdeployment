package com.example.file_service.entity;

import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * The CONTENT (the bytes) of one file, when StorageType = DATABASE.
 * Used ONLY by the database storage class (step F3). When we move to Azure Blob,
 * this table is no longer used for new files.
 */
@Entity
@Table(name = "file_content")
public class StoredFileContent {

    public StoredFileContent() {
    }

    public StoredFileContent(String storageKey, byte[] content) {
        this.storageKey = storageKey;
        this.content = content;
    }

    // The same value as UploadedFile.storageKey
    @Id
    @Column(nullable = false)
    private String storageKey;

    // BYTEA = raw bytes. LAZY = load the bytes only when really needed.
    @Basic(fetch = FetchType.LAZY)
    @Column(nullable = false, columnDefinition = "BYTEA")
    private byte[] content;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void beforeFirstSave() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now(ZoneOffset.UTC);
        }
    }

    public String getStorageKey() { return storageKey; }

    public byte[] getContent() { return content; }

    public LocalDateTime getCreatedAt() { return createdAt; }
}