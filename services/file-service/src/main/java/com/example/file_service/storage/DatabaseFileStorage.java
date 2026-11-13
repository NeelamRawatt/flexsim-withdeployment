package com.example.file_service.storage;

import com.example.file_service.entity.StoredFileContent;
import com.example.file_service.enums.StorageType;
import com.example.file_service.repository.StoredFileContentRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Keeps the file bytes in PostgreSQL (table file_content).
 *
 * Active when app.storage.type=database, or when the setting is missing (the default for now).
 * @Transactional (default "REQUIRED") = JOIN the caller's transaction. So "save the details"
 * and "save the bytes" (step F4) are ONE unit: both are saved, or neither.
 */
@Component
@ConditionalOnProperty(name = "app.storage.type", havingValue = "database", matchIfMissing = true)
public class DatabaseFileStorage implements FileStorage {

    private final StoredFileContentRepository repository;

    public DatabaseFileStorage(StoredFileContentRepository repository) {
        this.repository = repository;
    }

    @Override
    public StorageType type() {
        return StorageType.DATABASE;
    }

    @Override
    @Transactional
    public void save(String storageKey, byte[] content) {
        repository.save(new StoredFileContent(storageKey, content));
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] read(String storageKey) {
        return repository.findById(storageKey)
                .map(StoredFileContent::getContent)
                .orElseThrow(() -> new StoredContentMissingException(storageKey));
    }

    @Override
    @Transactional
    public void delete(String storageKey) {
        repository.deleteById(storageKey);
    }
}