package com.example.file_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.file_service.client.ExperimentClient;
import com.example.file_service.dto.FileUploadResponse;
import com.example.file_service.dto.FileValidationResult;
import com.example.file_service.entity.UploadedFile;
import com.example.file_service.enums.FileCategory;
import com.example.file_service.enums.StorageType;
import com.example.file_service.event.FileValidatedEvent;
import com.example.file_service.repository.UploadedFileRepository;
import com.example.file_service.security.AuthenticatedUser;
import com.example.file_service.storage.FileStorage;
import com.example.file_service.validation.ExcelFileGuard;
import com.example.file_service.validation.InfeedFileValidator;
import com.example.file_service.validation.ZoneResourceFileValidator;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.web.server.ResponseStatusException;

/** Unit test of the upload rules. Everything around the service is a fake (mock). */
@ExtendWith(MockitoExtension.class)
class UploadedFileServiceTest {

    private static final AuthenticatedUser NEELAM = new AuthenticatedUser(5L, "neelam", "OPERATOR");
    private static final AuthenticatedUser BOB = new AuthenticatedUser(6L, "bob", "OPERATOR");
    private static final AuthenticatedUser ADMIN = new AuthenticatedUser(9L, "boss", "ADMIN");
    private static final byte[] CONTENT = "pretend-excel".getBytes(StandardCharsets.UTF_8);

    @Mock
    private UploadedFileRepository repository;
    @Mock
    private FileStorage storage;
    @Mock
    private ExcelFileGuard guard;
    @Mock
    private InfeedFileValidator infeedValidator;
    @Mock
    private ZoneResourceFileValidator zoneValidator;
    @Mock
    private ExperimentClient experimentClient;
    @Mock
    private OutboxService outboxService;

    private UploadedFileService service;

    /** A pretend transaction manager: the test has no database, so "begin/commit" do nothing. */
    static class NoDatabaseTransactions implements PlatformTransactionManager {
        @Override
        public TransactionStatus getTransaction(TransactionDefinition definition) {
            return new SimpleTransactionStatus();
        }

        @Override
        public void commit(TransactionStatus status) {
        }

        @Override
        public void rollback(TransactionStatus status) {
        }
    }

    @BeforeEach
    void setUp() {
        service = new UploadedFileService(repository, storage, guard, infeedValidator, zoneValidator,
                experimentClient, outboxService, new NoDatabaseTransactions());
    }

    private FileUploadResponse uploadInfeed() {
        return service.upload(FileCategory.INFEED, "infeed.xlsx", "application/vnd.ms-excel", CONTENT,
                1005L, LocalDate.of(2026, 10, 22), NEELAM, "user-token");
    }

    private static void assertStatus(Runnable call, HttpStatus expected) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(ResponseStatusException.class,
                e -> assertThat(e.getStatusCode()).isEqualTo(expected));
    }

    @Test
    void notARealExcelFileIsRefusedBeforeAnythingElse() {
        when(guard.check("infeed.xlsx", CONTENT)).thenReturn(Optional.of("The file content is not a real Excel file."));

        FileUploadResponse response = uploadInfeed();

        assertThat(response.valid()).isFalse();
        assertThat(response.errors()).containsExactly("The file content is not a real Excel file.");
        verifyNoInteractions(experimentClient, infeedValidator, repository, storage);
    }

    @Test
    void aFileWithMistakesIsRefusedAndNothingIsSaved() {
        when(guard.check(anyString(), any())).thenReturn(Optional.empty());
        when(infeedValidator.validate(CONTENT)).thenReturn(
                FileValidationResult.failed("Infeed file validation failed", List.of("Invalid TC 'X' at row 3")));

        FileUploadResponse response = uploadInfeed();

        assertThat(response.valid()).isFalse();
        assertThat(response.errors()).containsExactly("Invalid TC 'X' at row 3");
        verify(experimentClient).requireOwnedBy(1005L, NEELAM, "user-token");
        verify(repository, never()).saveAndFlush(any());
        verifyNoInteractions(storage, outboxService);   // refused file = no Kafka message
    }

    @Test
    void aGoodFileIsSavedWithAllItsDetails() {
        when(guard.check(anyString(), any())).thenReturn(Optional.empty());
        when(infeedValidator.validate(CONTENT)).thenReturn(FileValidationResult.ok("Infeed file is valid"));
        when(repository.findByExperimentIdAndFileCategory(1005L, FileCategory.INFEED)).thenReturn(Optional.empty());
        when(storage.type()).thenReturn(StorageType.DATABASE);
        when(repository.saveAndFlush(any(UploadedFile.class))).thenAnswer(inv -> inv.getArgument(0));
        when(repository.existsByExperimentIdAndFileCategory(1005L, FileCategory.INFEED)).thenReturn(true);
        when(repository.existsByExperimentIdAndFileCategory(1005L, FileCategory.ZONE_RESOURCE)).thenReturn(false);

        FileUploadResponse response = uploadInfeed();

        assertThat(response.valid()).isTrue();
        assertThat(response.filesReady()).isFalse();   // the zone file is still missing

        ArgumentCaptor<UploadedFile> saved = ArgumentCaptor.forClass(UploadedFile.class);
        verify(repository).saveAndFlush(saved.capture());
        UploadedFile file = saved.getValue();
        assertThat(file.getExperimentId()).isEqualTo(1005L);
        assertThat(file.getFileCategory()).isEqualTo(FileCategory.INFEED);
        assertThat(file.getFileExtension()).isEqualTo("xlsx");
        assertThat(file.getSizeBytes()).isEqualTo((long) CONTENT.length);
        assertThat(file.getSha256()).hasSize(64);
        assertThat(file.getUploadedBy()).isEqualTo("neelam");          // from the token
        assertThat(file.getUploadedByUserId()).isEqualTo(5L);
        assertThat(file.getStorageKey()).startsWith("experiments/1005/INFEED/");
        verify(storage).save(eq(file.getStorageKey()), eq(CONTENT));

        // ... and the Kafka message went into the outbox (same transaction)
        ArgumentCaptor<Object> message = ArgumentCaptor.forClass(Object.class);
        verify(outboxService).add(eq("file.validated"), eq("1005"), message.capture());
        FileValidatedEvent event = (FileValidatedEvent) message.getValue();
        assertThat(event.experimentId()).isEqualTo(1005L);
        assertThat(event.fileCategory()).isEqualTo("INFEED");
        assertThat(event.fileName()).isEqualTo("infeed.xlsx");
        assertThat(event.filesReady()).isFalse();
        assertThat(event.uploadedBy()).isEqualTo("neelam");
        assertThat(event.eventId()).isNotBlank();
        assertThat(event.occurredAt()).endsWith("Z");
    }

    @Test
    void aNewUploadReplacesTheOldFileOfTheSameType() {
        UploadedFile old = new UploadedFile();
        old.setStorageKey("experiments/1005/INFEED/old");
        when(guard.check(anyString(), any())).thenReturn(Optional.empty());
        when(infeedValidator.validate(CONTENT)).thenReturn(FileValidationResult.ok("Infeed file is valid"));
        when(repository.findByExperimentIdAndFileCategory(1005L, FileCategory.INFEED)).thenReturn(Optional.of(old));
        when(storage.type()).thenReturn(StorageType.DATABASE);
        when(repository.saveAndFlush(any(UploadedFile.class))).thenAnswer(inv -> inv.getArgument(0));

        uploadInfeed();

        verify(storage).delete("experiments/1005/INFEED/old");
        verify(repository).delete(old);
        verify(repository).flush();
    }

    @Test
    void twoUploadsAtTheSameMomentGive409() {
        when(guard.check(anyString(), any())).thenReturn(Optional.empty());
        when(infeedValidator.validate(CONTENT)).thenReturn(FileValidationResult.ok("Infeed file is valid"));
        when(repository.findByExperimentIdAndFileCategory(1005L, FileCategory.INFEED)).thenReturn(Optional.empty());
        when(storage.type()).thenReturn(StorageType.DATABASE);
        when(repository.saveAndFlush(any(UploadedFile.class)))
                .thenThrow(new DataIntegrityViolationException("uk_uploaded_file_experiment_category"));

        assertStatus(this::uploadInfeed, HttpStatus.CONFLICT);
        verifyNoInteractions(outboxService);   // nothing saved = no Kafka message
    }

    @Test
    void onlyTheUploaderOrAdminMayDownload() {
        UploadedFile file = new UploadedFile();
        file.setFileName("infeed.xlsx");
        file.setStorageKey("k");
        file.setUploadedBy("neelam");
        when(repository.findById(12L)).thenReturn(Optional.of(file));
        when(storage.read("k")).thenReturn(CONTENT);

        assertThat(service.download(12L, NEELAM).content()).isEqualTo(CONTENT);
        assertThat(service.download(12L, ADMIN).fileName()).isEqualTo("infeed.xlsx");
        assertStatus(() -> service.download(12L, BOB), HttpStatus.FORBIDDEN);
    }

    @Test
    void unknownFileGives404() {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        assertStatus(() -> service.details(99L), HttpStatus.NOT_FOUND);
    }

    @Test
    void deletingAnExperimentDeletesAllItsFilesAndTheirBytes() {
        UploadedFile infeed = new UploadedFile();
        infeed.setStorageKey("experiments/1006/INFEED/a");
        UploadedFile zone = new UploadedFile();
        zone.setStorageKey("experiments/1006/ZONE_RESOURCE/b");
        when(repository.findByExperimentIdOrderByFileCategoryAsc(1006L)).thenReturn(List.of(infeed, zone));

        int deleted = service.deleteAllForExperiment(1006L);

        assertThat(deleted).isEqualTo(2);
        verify(storage).delete("experiments/1006/INFEED/a");
        verify(storage).delete("experiments/1006/ZONE_RESOURCE/b");
        verify(repository).delete(infeed);
        verify(repository).delete(zone);
    }

    @Test
    void deletingAnExperimentWithoutFilesIsSafe() {
        when(repository.findByExperimentIdOrderByFileCategoryAsc(1006L)).thenReturn(List.of());

        assertThat(service.deleteAllForExperiment(1006L)).isZero();   // duplicate message = no harm
        verifyNoInteractions(storage);
    }

    @Test
    void sha256IsTheStandardFingerprint() {
        assertThat(UploadedFileService.sha256("abc".getBytes(StandardCharsets.UTF_8)))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }
}