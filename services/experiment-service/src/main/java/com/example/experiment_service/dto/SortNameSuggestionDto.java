package com.example.experiment_service.dto;

/**
 * One suggestion for the "sort name" box. A sort name is the chute number (3 digits)
 * followed by the cage number (2 digits). Example: chute 201, cage 5 -> "20105".
 * Only SPIRAL chutes have cages 1 to 13. Every other chute type uses cage 0.
 */
public record SortNameSuggestionDto(
        String sortName,
        Integer chuteId,
        Integer cageId,
        Integer zoneId,
        String zoneName,
        String chuteType,
        String label) {
}