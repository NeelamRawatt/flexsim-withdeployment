package com.example.file_service.enums;

/**
 * WHERE the file content is kept.
 * DATABASE   = in our own PostgreSQL (table file_content)   <- now
 * AZURE_BLOB = in Azure Blob Storage                         <- later (after all services are done)
 * Saved on every file row, so after the switch we still know where OLD files are.
 */
public enum StorageType {
    DATABASE,
    AZURE_BLOB
}