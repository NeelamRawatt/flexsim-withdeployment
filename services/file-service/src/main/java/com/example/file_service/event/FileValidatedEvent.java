package com.example.file_service.event;

/**
 * The message sent on topic "file.validated" when a VALID file was saved.
 * (A refused file is not saved, so it sends nothing.)
 *
 * Who reads it: experiment-service (later), to show "Files ready" on the screen.
 *
 * eventId     = unique id of THIS message (a reader can skip a message it has already handled)
 * experimentId, fileId, fileCategory, fileName = which file
 * filesReady  = true when the experiment now has BOTH files (INFEED and ZONE_RESOURCE)
 * uploadedBy  = username from the token
 * occurredAt  = when, in UTC, as text like "2026-10-22T09:30:00Z"
 */
public record FileValidatedEvent(
        String eventId,
        Long experimentId,
        Long fileId,
        String fileCategory,
        String fileName,
        boolean filesReady,
        String uploadedBy,
        String occurredAt) {
}