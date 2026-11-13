package com.example.file_service.controller;

import com.example.file_service.dto.FileDetailsDto;
import com.example.file_service.dto.FileDownload;
import com.example.file_service.dto.FileUploadResponse;
import com.example.file_service.enums.FileCategory;
import com.example.file_service.security.AuthenticatedUser;
import com.example.file_service.security.JwtAuthenticationFilter;
import com.example.file_service.service.UploadedFileService;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * The URLs of file-service. The controller only receives and answers; the rules are in UploadedFileService.
 *
 * Upload = "multipart/form-data": the way a browser sends a FILE plus some form fields together.
 * In Postman: Body -> form-data -> key "file" of type File, plus text keys experimentId and fileUploadDate.
 *
 * Who may do what:
 *   upload                       -> OPERATOR, ADMIN, DEVELOPER (and only for their OWN experiment)
 *   details / list / exists      -> any logged-in user
 *   download (the bytes)         -> the uploader, ADMIN or DEVELOPER
 */
@RestController
@RequestMapping("/api/files")
public class FileController {

    private final UploadedFileService service;

    public FileController(UploadedFileService service) {
        this.service = service;
    }

    @PostMapping(path = "/validate/infeed", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('OPERATOR','ADMIN','DEVELOPER')")
    public ResponseEntity<FileUploadResponse> uploadInfeed(
            @RequestParam("file") MultipartFile file,
            @RequestParam("experimentId") Long experimentId,
            @RequestParam(value = "fileUploadDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fileUploadDate,
            @AuthenticationPrincipal AuthenticatedUser user,
            HttpServletRequest request) throws IOException {
        return upload(FileCategory.INFEED, file, experimentId, fileUploadDate, user, request);
    }

    @PostMapping(path = "/validate/zone-resource", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('OPERATOR','ADMIN','DEVELOPER')")
    public ResponseEntity<FileUploadResponse> uploadZoneResource(
            @RequestParam("file") MultipartFile file,
            @RequestParam("experimentId") Long experimentId,
            @RequestParam(value = "fileUploadDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fileUploadDate,
            @AuthenticationPrincipal AuthenticatedUser user,
            HttpServletRequest request) throws IOException {
        return upload(FileCategory.ZONE_RESOURCE, file, experimentId, fileUploadDate, user, request);
    }

    private ResponseEntity<FileUploadResponse> upload(FileCategory category, MultipartFile file, Long experimentId,
                                                      LocalDate uploadDate, AuthenticatedUser user,
                                                      HttpServletRequest request) throws IOException {
        FileUploadResponse response = service.upload(
                category,
                file.getOriginalFilename(),
                file.getContentType(),
                file.getBytes(),
                experimentId,
                uploadDate,
                user,
                JwtAuthenticationFilter.resolveToken(request));   // the user's token, passed to experiment-service
        return ResponseEntity.status(response.valid() ? HttpStatus.CREATED : HttpStatus.BAD_REQUEST).body(response);
    }

    @GetMapping("/{fileId}")
    public FileDetailsDto details(@PathVariable Long fileId) {
        return service.details(fileId);
    }

    @GetMapping("/byExperiment/{experimentId}")
    public List<FileDetailsDto> listForExperiment(@PathVariable Long experimentId) {
        return service.listForExperiment(experimentId);
    }

    /** true / false. Same answer shape as your team's file-service, so simulation-service can use it. */
    @GetMapping("/exists/{experimentId}")
    public boolean filesReady(@PathVariable Long experimentId) {
        return service.filesReady(experimentId);
    }

    /** Download the file itself. "attachment" = the browser saves it instead of trying to show it. */
    @GetMapping("/{fileId}/content")
    public ResponseEntity<byte[]> download(@PathVariable Long fileId, @AuthenticationPrincipal AuthenticatedUser user) {
        FileDownload file = service.download(fileId, user);
        MediaType type = file.contentType() == null
                ? MediaType.APPLICATION_OCTET_STREAM
                : MediaType.parseMediaType(file.contentType());
        return ResponseEntity.ok()
                .contentType(type)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(file.fileName(), StandardCharsets.UTF_8).build().toString())
                .body(file.content());
    }
}