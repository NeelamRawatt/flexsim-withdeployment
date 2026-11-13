package com.example.file_service.service;

import com.example.file_service.client.ExperimentClient;
import com.example.file_service.dto.FileDetailsDto;
import com.example.file_service.dto.FileDownload;
import com.example.file_service.dto.FileUploadResponse;
import com.example.file_service.dto.FileValidationResult;
import com.example.file_service.entity.UploadedFile;
import com.example.file_service.enums.FileCategory;
import com.example.file_service.event.FileValidatedEvent;
import com.example.file_service.event.KafkaTopics;
import com.example.file_service.repository.UploadedFileRepository;
import com.example.file_service.security.AuthenticatedUser;
import com.example.file_service.storage.FileStorage;
import com.example.file_service.validation.ExcelFileGuard;
import com.example.file_service.validation.InfeedFileValidator;
import com.example.file_service.validation.ZoneResourceFileValidator;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

/**
 * The business rules of file-service.
 *
 * UPLOAD, in this order (cheap checks first, the database last):
 *   1. door check   (ExcelFileGuard)       -> 400 if not a real Excel file
 *   2. owner check  (experiment-service)   -> 404 / 403 / 503
 *   3. content check (the validator)       -> 400 with every mistake
 *   4. ONE transaction: delete the old file of the same type (REPLACE), save the new bytes + details,
 *      AND write the "file.validated" Kafka message into the outbox (saved together, or not at all)
 *
 * Why is step 4 a separate small transaction (TransactionTemplate)?
 * A transaction holds a database connection. We do NOT want to hold one while we wait
 * for experiment-service or read a 10 MB Excel file. So only step 4 is inside it.
 */
@Service
public class UploadedFileService {

    private static final Logger log = LoggerFactory.getLogger(UploadedFileService.class);

    private final UploadedFileRepository repository;
    private final FileStorage storage;
    private final ExcelFileGuard guard;
    private final InfeedFileValidator infeedValidator;
    private final ZoneResourceFileValidator zoneValidator;
    private final ExperimentClient experimentClient;
    private final OutboxService outboxService;
    private final TransactionTemplate transaction;

    /** What the save step gives back: the saved file, and whether BOTH files now exist. */
    private record SavedUpload(UploadedFile file, boolean filesReady) {
    }

    public UploadedFileService(UploadedFileRepository repository,
                               FileStorage storage,
                               ExcelFileGuard guard,
                               InfeedFileValidator infeedValidator,
                               ZoneResourceFileValidator zoneValidator,
                               ExperimentClient experimentClient,
                               OutboxService outboxService,
                               PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.storage = storage;
        this.guard = guard;
        this.infeedValidator = infeedValidator;
        this.zoneValidator = zoneValidator;
        this.experimentClient = experimentClient;
        this.outboxService = outboxService;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    public FileUploadResponse upload(FileCategory category, String fileName, String contentType, byte[] content,
                                     Long experimentId, LocalDate uploadDate, AuthenticatedUser user, String token) {
        // 1. door check
        Optional<String> problem = guard.check(fileName, content);
        if (problem.isPresent()) {
            return FileUploadResponse.rejected("File was not accepted", List.of(problem.get()));
        }

        // 2. the experiment must exist and belong to this user (throws 404 / 403 / 503)
        experimentClient.requireOwnedBy(experimentId, user, token);

        // 3. content check
        FileValidationResult result = category == FileCategory.INFEED
                ? infeedValidator.validate(content)
                : zoneValidator.validate(content);
        if (!result.valid()) {
            return FileUploadResponse.rejected(result.message(), result.errors());
        }

        // 4. save (REPLACE the old file of the same type) + outbox message, in ONE transaction
        SavedUpload saved;
        try {
            saved = transaction.execute(status -> {
                UploadedFile file = replaceAndSave(category, fileName, contentType, content, experimentId, uploadDate, user);
                boolean ready = filesReady(experimentId);   // sees the new row: same transaction
                outboxService.add(
                        KafkaTopics.FILE_VALIDATED,
                        String.valueOf(experimentId),       // key = experimentId -> keeps order per experiment
                        new FileValidatedEvent(
                                UUID.randomUUID().toString(),
                                experimentId,
                                file.getId(),
                                category.name(),
                                file.getFileName(),
                                ready,
                                user.username(),
                                Instant.now().toString()));
                return new SavedUpload(file, ready);
            });
        } catch (DataIntegrityViolationException e) {
            // Two uploads of the same type for the same experiment at the SAME moment:
            // the database rule (one file per type) stopped the second one.
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Another upload of this file type is running for this experiment. Please try again.");
        }

        log.info("{} file {} saved for experiment {} by {} (files ready: {})",
                category, saved.file().getId(), experimentId, user.username(), saved.filesReady());
        return FileUploadResponse.accepted(result.message(), FileDetailsDto.from(saved.file()), saved.filesReady());
    }

    private UploadedFile replaceAndSave(FileCategory category, String fileName, String contentType, byte[] content,
                                        Long experimentId, LocalDate uploadDate, AuthenticatedUser user) {
        repository.findByExperimentIdAndFileCategory(experimentId, category).ifPresent(old -> {
            // NOTE for the Azure step: there, the old blob must be deleted AFTER the commit,
            // because Azure is not part of our database transaction.
            storage.delete(old.getStorageKey());
            repository.delete(old);
            repository.flush();   // the DELETE must reach the database BEFORE the INSERT (unique rule)
        });

        // The address of the bytes. Later in Azure this is the blob name.
        String storageKey = "experiments/" + experimentId + "/" + category + "/" + UUID.randomUUID();
        storage.save(storageKey, content);

        UploadedFile file = new UploadedFile();
        file.setExperimentId(experimentId);
        file.setFileCategory(category);
        file.setFileName(fileName);
        file.setFileExtension(ExcelFileGuard.extensionOf(fileName));
        file.setContentType(contentType);
        file.setSizeBytes((long) content.length);
        file.setSha256(sha256(content));
        file.setStorageType(storage.type());
        file.setStorageKey(storageKey);
        file.setUploadDate(uploadDate);
        file.setUploadedBy(user.username());
        file.setUploadedByUserId(user.userId());
        return repository.saveAndFlush(file);
    }

    @Transactional(readOnly = true)
    public FileDetailsDto details(Long fileId) {
        return FileDetailsDto.from(findOrThrow(fileId));
    }

    @Transactional(readOnly = true)
    public List<FileDetailsDto> listForExperiment(Long experimentId) {
        return repository.findByExperimentIdOrderByFileCategoryAsc(experimentId).stream()
                .map(FileDetailsDto::from)
                .toList();
    }

    /** true when the experiment has BOTH files. Asked by simulation-service before a run. */
    @Transactional(readOnly = true)
    public boolean filesReady(Long experimentId) {
        return repository.existsByExperimentIdAndFileCategory(experimentId, FileCategory.INFEED)
                && repository.existsByExperimentIdAndFileCategory(experimentId, FileCategory.ZONE_RESOURCE);
    }

    /**
     * The bytes of a file. Only the person who uploaded it, or ADMIN / DEVELOPER, may download.
     * (Later the flexsim-worker gets its own service role here.)
     */
    @Transactional(readOnly = true)
    public FileDownload download(Long fileId, AuthenticatedUser user) {
        UploadedFile file = findOrThrow(fileId);
        boolean isPowerUser = "ADMIN".equals(user.role()) || "DEVELOPER".equals(user.role());
        if (!isPowerUser && !user.username().equals(file.getUploadedBy())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You are not allowed to download this file");
        }
        return new FileDownload(file.getFileName(), file.getContentType(), storage.read(file.getStorageKey()));
    }

    /**
     * Deletes ALL files of one experiment (details + bytes), in ONE transaction.
     * Called by the Kafka listener when the experiment was deleted in experiment-service.
     * Returns how many files were deleted. 0 is normal (no files, or a duplicate message).
     */
    @Transactional
    public int deleteAllForExperiment(Long experimentId) {
        List<UploadedFile> files = repository.findByExperimentIdOrderByFileCategoryAsc(experimentId);
        for (UploadedFile file : files) {
            storage.delete(file.getStorageKey());
            repository.delete(file);
        }
        return files.size();
    }

    private UploadedFile findOrThrow(Long fileId) {
        return repository.findById(fileId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "File not found with id: " + fileId));
    }

    /** The fingerprint of the bytes, as 64 hex characters. */
    static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is always available in Java", e);
        }
    }
}