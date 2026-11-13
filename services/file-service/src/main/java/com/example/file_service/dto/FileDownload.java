package com.example.file_service.dto;

/** A file ready to be sent back: name, type and the bytes. Used only inside the service. */
public record FileDownload(String fileName, String contentType, byte[] content) {
}