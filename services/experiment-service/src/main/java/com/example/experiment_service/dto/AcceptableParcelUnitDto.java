package com.example.experiment_service.dto;

/** The allowed parcel size and weight limits of one experiment (send and read). */
public record AcceptableParcelUnitDto(
        Long experimentId,
        Double minHeight,
        Double maxHeight,
        Double minLength,
        Double maxLength,
        Double minWidth,
        Double maxWidth,
        Double minWeight,
        Double maxWeight) {
}