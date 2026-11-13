package com.example.file_service.enums;

/**
 * The 2 kinds of Excel files an experiment needs.
 * An experiment is READY to run only when it has BOTH.
 */
public enum FileCategory {
    INFEED,
    ZONE_RESOURCE
}