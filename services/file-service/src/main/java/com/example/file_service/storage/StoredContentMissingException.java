package com.example.file_service.storage;

/** The file details exist, but the bytes are gone. Should never happen; if it does, it is a real problem. */
public class StoredContentMissingException extends RuntimeException {

    public StoredContentMissingException(String storageKey) {
        super("No stored content for key " + storageKey);
    }
}