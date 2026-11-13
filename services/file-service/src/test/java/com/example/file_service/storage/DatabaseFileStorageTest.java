package com.example.file_service.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.file_service.entity.StoredFileContent;
import com.example.file_service.enums.StorageType;
import com.example.file_service.repository.StoredFileContentRepository;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DatabaseFileStorageTest {

    @Mock
    private StoredFileContentRepository repository;

    @Test
    void savesReadsAndDeletesByKey() {
        DatabaseFileStorage storage = new DatabaseFileStorage(repository);
        byte[] bytes = {1, 2, 3};

        storage.save("experiments/1005/INFEED/abc", bytes);
        ArgumentCaptor<StoredFileContent> saved = ArgumentCaptor.forClass(StoredFileContent.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getStorageKey()).isEqualTo("experiments/1005/INFEED/abc");
        assertThat(saved.getValue().getContent()).containsExactly(1, 2, 3);

        when(repository.findById("experiments/1005/INFEED/abc")).thenReturn(Optional.of(saved.getValue()));
        assertThat(storage.read("experiments/1005/INFEED/abc")).containsExactly(1, 2, 3);

        storage.delete("experiments/1005/INFEED/abc");
        verify(repository).deleteById("experiments/1005/INFEED/abc");

        assertThat(storage.type()).isEqualTo(StorageType.DATABASE);
    }

    @Test
    void readingAMissingKeyGivesAClearError() {
        when(repository.findById(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> new DatabaseFileStorage(repository).read("nope"))
                .isInstanceOf(StoredContentMissingException.class);
    }
}