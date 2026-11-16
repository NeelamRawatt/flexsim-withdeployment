package com.example.experiment_service.dto;

/** Answer of POST /api/parcels/getParcelCount: {"parcelCount": 52501} */
public record ParcelCountResponseDto(Long parcelCount) {
}
