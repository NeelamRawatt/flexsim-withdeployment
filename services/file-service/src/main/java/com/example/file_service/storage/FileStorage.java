package com.example.file_service.storage;

import com.example.file_service.enums.StorageType;

/**
 * WHERE the bytes of a file are kept. The rest of the service only talks to THIS interface.
 *
 * Interface = a list of methods a class promises to have, without saying HOW.
 *   Now:   DatabaseFileStorage   (PostgreSQL, table file_content)
 *   Later: AzureBlobFileStorage  (Azure Blob Storage) - switched with ONE setting:
 *          app.storage.type=azure-blob
 * Nothing else in the service changes when we switch.
 *
 * storageKey = the address of the content, example "experiments/1005/INFEED/3f2a...".
 *              (In Azure it becomes the blob name.)
 */
public interface FileStorage {

    /** Which storage this is (saved on every file row). */
    StorageType type();

    /** Saves the bytes under this key. */
    void save(String storageKey, byte[] content);

    /** Reads the bytes. Throws StoredContentMissingException if nothing is stored under the key. */
    byte[] read(String storageKey);

    /** Deletes the bytes. Does nothing if the key does not exist (safe to call twice). */
    void delete(String storageKey);
}